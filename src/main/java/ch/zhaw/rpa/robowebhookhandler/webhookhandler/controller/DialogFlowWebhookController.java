package ch.zhaw.rpa.robowebhookhandler.webhookhandler.controller;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.json.JsonGenerator;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2IntentMessage;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2IntentMessageText;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2QueryResult;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2WebhookRequest;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2WebhookResponse;

import ch.zhaw.rpa.robowebhookhandler.webhookhandler.handler.UiPathHandler;
import ch.zhaw.rpa.robowebhookhandler.webhookhandler.restclients.UiPathOrchestratorRestClient;

@RestController
@RequestMapping(value = "api")
public class DialogFlowWebhookController {

    @Autowired
    private GsonFactory gsonFactory;

    @Autowired
    private UiPathHandler uiPathHandler;

    @Autowired
    private UiPathOrchestratorRestClient client;

    @GetMapping(value = "/test")
    public String testApi() {
        System.out.println("!!!!!!!!! Test Request received");
        return "Yes, it works";
    }

    @PostMapping(value = "/dialogflow-main-handler", produces = { MediaType.APPLICATION_JSON_VALUE })
    public String webhook(@RequestBody String rawData) throws IOException {
        // Response instanzieren
        GoogleCloudDialogflowV2WebhookResponse response = new GoogleCloudDialogflowV2WebhookResponse();

        // Antwort-Message instanzieren
        GoogleCloudDialogflowV2IntentMessage msg = new GoogleCloudDialogflowV2IntentMessage();

        // RequestBody parsen
        GoogleCloudDialogflowV2WebhookRequest request = gsonFactory
                .createJsonParser(rawData)
                .parse(GoogleCloudDialogflowV2WebhookRequest.class);

        // Query Result erhalten
        GoogleCloudDialogflowV2QueryResult queryResult = request.getQueryResult();

        // Intent auslesen
        String intent = queryResult.getIntent().getDisplayName();

        //ANPASSEN!!!!
        // Je nach Intent anderen Handler aufrufen oder Response zusammenbauen
        if (intent.equals("rechnungsdetails.abrufen")|| intent.equals("ContinueGetRechnungsdetailsIntent")) {
            // Antwort vom RPA-Bot erhalten
            try {
                Object rechnungsnummerObject = request.getQueryResult().getParameters().get("Rechnungsnummer");
                String rechnungsnummer = rechnungsnummerObject != null ? rechnungsnummerObject.toString() : "";
                //hier wird die Rechnungsnummer and RPA-Bot übergeben, in_InvoiceNr
                JSONObject inputArguments = new JSONObject();
                System.out.println("Webhhok Controller in_RGNummer: "+rechnungsnummer);
                inputArguments.put("in_RGNummer", rechnungsnummer);
                String releaseKey = client.getReleaseKeyByProcessKey("RechnungenAuslesen");
                msg = uiPathHandler.handleUiPathRequest(request, intent, msg, inputArguments, releaseKey);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else if(intent.equals("rechnungen.genehmigen - yes") || intent.equals("rechnungen.genehmigen - no")) {
            try {
                Object rechnungsnummerObject = request.getQueryResult().getOutputContexts().get(0).getParameters().get("number");
                String rechnungsnummer = rechnungsnummerObject != null ? rechnungsnummerObject.toString() : "";
                //Object genehmigungObject = request.getQueryResult().getParameters().get("genehmigung");
                Object genehmigungObject = request.getQueryResult().getOutputContexts().get(0).getParameters().get("genehmigung");
                String genehmigung = genehmigungObject != null ? genehmigungObject.toString() : "";
                System.out.println("Webhhok Controller loc_RechnungsNr: " + rechnungsnummer);
                System.out.println("Webhhok Controller loc_Genehmigt: " + genehmigung);
                JSONObject inputArguments = new JSONObject();
                inputArguments.put("loc_RechnungsNr", rechnungsnummer);
                inputArguments.put("loc_Genehmigt", genehmigung);
                String releaseKey = client.getReleaseKeyByProcessKey("Odoo-Rechnungs-Genehmigung-Einzel");
                msg = uiPathHandler.handleUiPathRequest(request, intent, msg, inputArguments, releaseKey);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            // Response no handler found zusammenstellen
            GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
            text.setText(List.of("There's no handler for '" + intent + "'"));
            msg.setText(text);
        }

        // Webhook-Response für Dialogflow aufbereiten und zurückgeben
        response.setFulfillmentMessages(List.of(msg));
        StringWriter stringWriter = new StringWriter();
        JsonGenerator jsonGenerator = gsonFactory.createJsonGenerator(stringWriter);
        jsonGenerator.enablePrettyPrint();
        jsonGenerator.serialize(response);
        jsonGenerator.flush();
        return stringWriter.toString();
    }
}
