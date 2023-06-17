package ch.zhaw.rpa.robowebhookhandler.webhookhandler.restclients;

import javax.annotation.PostConstruct;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class UiPathOrchestratorRestClient {

    private RestTemplate restTemplate;

    @Autowired
    private RestTemplateBuilder builder;

    @Value("${uipath.tenant-name}")
    private String tenantName;

    @Value("${uipath.root-uri}")
    private String rootUri;

    @Value("${uipath.auth-uri}")
    private String authUri;

    @Value("${uipath.client-id}")
    private String clientId;

    @Value("${uipath.user-key}")
    private String userKey;

    @Value("${uipath.folder-id}")
    private String folderId;

    private HttpHeaders httpHeaders;

    @PostConstruct
    public void postConstruct() {
        this.restTemplate = this.builder
                .rootUri(rootUri)
                .build();

        httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.set("X-UIPATH-TenantName", tenantName);
        httpHeaders.set("X-UIPATH-OrganizationUnitId", folderId);
    }

    public String getReleaseKeyByProcessKey(String processKey) {
        this.authenticate();

        String uri = "/odata/Releases?$filter=ProcessKey eq '" + processKey + "'";

        HttpEntity<String> httpEntity = new HttpEntity<>(httpHeaders);

        try {
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, httpEntity, String.class);
            JSONObject responseBody = new JSONObject(response.getBody());
            Integer resultCount = responseBody.getInt("@odata.count");
            if (resultCount != 1) {
                return "";
            } else {
                String releaseKey = responseBody.getJSONArray("value").getJSONObject(0).getString("Key");
                return releaseKey;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    public Integer startJobAndGetId(String releaseKey, JSONObject inputArguments) {
        this.authenticate();

        String uri = "/odata/Jobs/UiPath.Server.Configuration.OData.StartJobs";

        // Generate Startinfo
        JSONObject startInfo = new JSONObject();
        startInfo.put("ReleaseKey", releaseKey);
        startInfo.put("JobsCount", 1);
        startInfo.put("Source", "Manual");
        startInfo.put("Strategy", "JobsCount");
        startInfo.put("InputArguments", inputArguments.toString());

        // Generate Body
        JSONObject body = new JSONObject();
        body.put("startInfo", startInfo);

        RequestEntity<String> requestEntity = RequestEntity
                .post(uri)
                .headers(httpHeaders)
                .body(body.toString());

        try {
            ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);

            JSONObject responseBody = new JSONObject(response.getBody());

            Integer id = responseBody.getJSONArray("value").getJSONObject(0).getInt("Id");
            return id;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public JSONObject getJobById(Integer id, Integer pollingCycleInMilliseconds, Integer pollingMaxRetries) {
        this.authenticate();

        String uri = "/odata/Jobs(" + id + ")";

        HttpEntity<String> httpEntity = new HttpEntity<>(httpHeaders);

        String jobState = "";
        Integer pollingCounter = 0;
        ResponseEntity<String> response;

        do {
            try {
                Thread.sleep(pollingCycleInMilliseconds);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
                return null;
            }

            try {
                response = restTemplate.exchange(uri, HttpMethod.GET, httpEntity, String.class);
                JSONObject responseBody = new JSONObject(response.getBody());
                jobState = responseBody.getString("State");
                if (jobState.equals("Successful")) {
                    JSONObject outputArguments = new JSONObject(responseBody.getString("OutputArguments"));
                    return outputArguments;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } while (!jobState.equals("Successful") || pollingCounter <= pollingMaxRetries);

        return null;
    }

    // Eigentlich müsste man dies nur alle 24 h machen, respektive anders über
    // Refresh Token, aber zur Sicherheit, wird diese Methode halt bei jedem Request
    // zunächst aufgerufen
    private void authenticate() {
        // Generate Body
        JSONObject body = new JSONObject();
        body.put("grant_type", "refresh_token");
        body.put("client_id", clientId);
        body.put("refresh_token", userKey);

        RequestEntity<String> requestEntity = RequestEntity
                .post(authUri)
                .headers(httpHeaders)
                .body(body.toString());
        try {
            ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);

            JSONObject responseBody = new JSONObject(response.getBody());
            String accessToken = responseBody.getString("access_token");
            httpHeaders.setBearerAuth(accessToken);
            System.out.println("!!!!!!!!! UiPath REST Authentication successful");
        } catch (Exception e) {
            System.out.println("!!!!!!!!! UiPath REST Authentication failed");
            e.printStackTrace();
        }

    }
}
