package ch.zhaw.rpa.robowebhookhandler.webhookhandler.asynchandling;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import ch.zhaw.rpa.robowebhookhandler.webhookhandler.restclients.UiPathOrchestratorRestClient;

@Component
public class UiPathAsyncJobHandler {

    @Autowired
    private UiPathOrchestratorRestClient client;

    @Async
    public void asyncRunUiPathRoboConnector(DialogFlowSessionState sessionState, JSONObject inputArguments,
            String releaseKey) {
        //System.out.println("!!!!!!!!! Release Key angefordert von UiPath");

        System.out.println("!!!!!!!!! Auftrag für Job starten erteilt");
        Integer id = client.startJobAndGetId(releaseKey, inputArguments);

        if (id == 0) {
            System.out.println("!!!!!!!!! Auftrag für Job starten fehlgeschlagen");
            sessionState.setUiPathJobState("failed");
        } else {
            System.out.println("!!!!!!!!! Auftrag für Job starten erfolgreich");
            sessionState.setUiPathJobState("created");
            JSONObject outputArguments = client.getJobById(id, 1000, 60);

            if (outputArguments == null || !outputArguments.getString("out_InvoiceError").isEmpty()) {
                System.out.println("!!!!!!!!! Job fehlgeschlagen");
                sessionState.setUiPathJobState("failed");
                sessionState.setUiPathExceptionMessage(outputArguments == null ? "Der Job ist fehlgeschlagen"
                        : outputArguments.getString("out_InvoiceError"));
            } else {
                System.out.println("!!!!!!!!! Job erfolgreich durchgeführt");
                sessionState.setUiPathJobState("successfull");
                sessionState.setOutputArguments(outputArguments);
                System.out.println(
                        "UiPathAsynchJobHandler Message: " + outputArguments.getString("out_InvoiceInformation"));
            }

        }
    }
}
