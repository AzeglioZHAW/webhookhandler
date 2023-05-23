package ch.zhaw.rpa.robowebhookhandler.webhookhandler.handler;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2IntentMessage;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2WebhookRequest;

import ch.zhaw.rpa.robowebhookhandler.webhookhandler.asynchandling.DialogFlowSessionState;
import ch.zhaw.rpa.robowebhookhandler.webhookhandler.asynchandling.DialogFlowSessionStateService;
import ch.zhaw.rpa.robowebhookhandler.webhookhandler.asynchandling.UiPathAsyncJobHandler;

@Component
public class UiPathHandler {
    @Autowired
    private UiPathAsyncJobHandler uiPathAsyncJobHandler;

    @Autowired
    private DialogFlowSessionStateService stateService;

    public GoogleCloudDialogflowV2IntentMessage handleUiPathRequest(GoogleCloudDialogflowV2WebhookRequest request,
            String intent, GoogleCloudDialogflowV2IntentMessage msg) {
        // ANPASSEN!!!!
        // Rechnungsnummer auslesen
        String rechnungsnummer = request.getQueryResult().getParameters().get("Rechnung")
                .toString();
        System.out.println(rechnungsnummer);
        // Session Id auslesen
        String sessionId = request.getSession();

        // Prüfen, ob Session Id bereits verwaltet ist
        DialogFlowSessionState sessionState = stateService.getSessionStateBySessionId(sessionId);

        // Wenn die Session Id noch nicht verwaltet ist (erster Request)
        if (sessionState == null) {
            // Neuen Session State erstellen
            sessionState = DialogFlowSessionState.builder().DialogFlowSessionId(sessionId)
                    .DialogFlowFirstRequestReceived(new Date()).uiPathExceptionMessage("").build();

            stateService.addSessionState(sessionState);

            // Async den Auftrag für den UiPath-Job erteilen
            uiPathAsyncJobHandler.asyncRunUiPathRoboConnector(sessionState, rechnungsnummer);
            System.out.println("!!!!!!!!! AsyncHandler aufgerufen für Session Id " + sessionId);

            // Etwas Zeit "schinden", aber so, dass DialogFlow noch nicht abbricht und
            // Text für Benutzer festlegen
            msg = getResponseOfTypePleaseWait(
                    "It can take a minute to get your information from the original source. Click on 'Continue' as soon you want to see, if the information is already there.",
                    request, intent, msg);
        } 
       



        System.out.println(msg);
        return msg;
    }

    private GoogleCloudDialogflowV2IntentMessage getResponseOfTypePleaseWait(String promptText,
                        GoogleCloudDialogflowV2WebhookRequest request,
                        String intent, GoogleCloudDialogflowV2IntentMessage msg) {
                try {
                        Thread.sleep(4000);
                } catch (InterruptedException e) {
                        promptText = "The following error occured: " + e.getLocalizedMessage()
                                        + "Click on 'Continue' if you want to try it again.";
                }

                // Rich-Content-Payload in Form von verschachtelten HashMaps aufbereiten
                // basierend auf
                // https://cloud.google.com/dialogflow/es/docs/integrations/dialogflow-messenger?hl=en#rich
                String textArray[] = new String[] { promptText };

                Map<String, Object> descriptionMap = new HashMap<>();
                descriptionMap.put("type", "description");
                descriptionMap.put("title", "Please wait ...");
                descriptionMap.put("text", textArray);

                Map<String, Object> parametersMap = new HashMap<>();

                Map<String, Object> eventMap = new HashMap<>();
                eventMap.put("name",
                                (intent.equals("ContinueGetImageIntent") || intent.equals("ImageIntent")
                                                ? "ContinueImageEvent"
                                                : "ContinueDescriptionEvent"));
                eventMap.put("languageCode", "en");
                eventMap.put("parameters", parametersMap);

                Map<String, Object> iconMap = new HashMap<>();
                iconMap.put("type", "chevron_right");
                iconMap.put("color", "#FF9800");

                Map<String, Object> linkMap = new HashMap<>();
                linkMap.put("type", "button");
                linkMap.put("text", "Continue");
                linkMap.put("event", eventMap);
                linkMap.put("icon", iconMap);

                Object richContentInnerArray[] = new Object[] { descriptionMap, linkMap };

                Object richContentOuterArray[] = new Object[] { richContentInnerArray };

                Map<String, Object> richContentMap = new HashMap<>();
                richContentMap.put("richContent", richContentOuterArray);
                msg.setPayload(richContentMap);

                return msg;
        }
}
