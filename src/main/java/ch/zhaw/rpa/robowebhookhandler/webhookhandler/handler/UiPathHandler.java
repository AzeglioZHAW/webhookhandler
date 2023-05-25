package ch.zhaw.rpa.robowebhookhandler.webhookhandler.handler;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2IntentMessage;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2IntentMessageText;
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
                String rechnungsnummer = request.getQueryResult().getParameters().get("rechnung")
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
                                        "Es kann eine Minute dauern, bis die Informationen von der Originalquelle abgerufen werden. Klicken Sie auf 'Weiter', sobald Sie sehen möchten, ob die Informationen bereits vorhanden sind.",
                                        request, intent, msg);
                }

                // Wenn ein zweiter, dritter, usw. Request vorhanden ist
                else {
                        // Wenn der UiPath Job noch am laufen ist
                        if (sessionState.getUiPathJobState().equals("created")) {
                                // Etwas Zeit "schinten", aber so, dass Google Actions noch nicht abbricht und
                                // Text für Benutzer festlegen
                                msg = getResponseOfTypePleaseWait(
                                                "Ich erhalte immer noch die von Ihnen gewünschten Informationen. Klicken Sie auf 'Weiter', wenn Sie die Umfrage wiederholen möchten.",
                                                request, intent, msg);
                        }
                        // Wenn der UiPath Job abgeschlossen wurde
                        else if (sessionState.getUiPathJobState().equals("successfull")) {
                                /*
                                 * String dogDetailsUri = sessionState.getOutputArguments()
                                 * .getString("out_uriDetailsPage");
                                 */

                                // Wenn die Rechnungsdetails angefragt wurden
                                if (intent.equals("rechnungsdetails.abrufen")
                                                || intent.equals("ContinueGetRechnungsdetailsIntent")) {
                                        String OutRechnungsDetails = sessionState.getOutputArguments()
                                                        .getString("out_InvoiceInformation");
                                        System.out.println(OutRechnungsDetails);
                                        GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
                                        text.setText(List.of("Die Rechnungsdetails sind: " + OutRechnungsDetails));
                                        msg.setText(text);

                                }

                                stateService.removeSessionState(sessionState);
                        }
                        // In allen anderen Fällen (UiPath Job nicht erstellt werden konnte oder
                        // fehlgeschlagen)
                        else {
                                GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
                                text.setText(List.of((sessionState.getUiPathExceptionMessage().isEmpty()
                                                ? "An unexpected error occured."
                                                : "The following error occured: "
                                                                + sessionState.getUiPathExceptionMessage())));
                                msg.setText(text);
                                stateService.removeSessionState(sessionState);
                        }

                }

                System.out.println("UiPathHandler Return msg: " + msg);
                return msg;
        }

        private GoogleCloudDialogflowV2IntentMessage getResponseOfTypePleaseWait(String promptText,
                        GoogleCloudDialogflowV2WebhookRequest request,
                        String intent, GoogleCloudDialogflowV2IntentMessage msg) {
                try {
                        Thread.sleep(4000);
                } catch (InterruptedException e) {
                        promptText = "Der folgende Fehler ist aufgetreten: " + e.getLocalizedMessage()
                                        + "Klicken Sie auf 'Weiter', wenn Sie es erneut versuchen möchten.";
                }

                // Rich-Content-Payload in Form von verschachtelten HashMaps aufbereiten
                // basierend auf
                // https://cloud.google.com/dialogflow/es/docs/integrations/dialogflow-messenger?hl=en#rich
                String textArray[] = new String[] { promptText };

                Map<String, Object> descriptionMap = new HashMap<>();
                descriptionMap.put("type", "description");
                descriptionMap.put("title", "Bitte warten ...");
                descriptionMap.put("text", textArray);

                Map<String, Object> parametersMap = new HashMap<>();

                Map<String, Object> eventMap = new HashMap<>();
                eventMap.put("name",
                                (intent.equals("ContinueGetRechnungsdetailsIntent")
                                                || intent.equals("rechnungsdetails.abrufen")
                                                                ? "ContinueGetRechnungsDetailsEvent"
                                                                : ""));
                eventMap.put("languageCode", "de");
                eventMap.put("parameters", parametersMap);

                Map<String, Object> iconMap = new HashMap<>();
                iconMap.put("type", "chevron_right");
                iconMap.put("color", "#FF9800");

                Map<String, Object> linkMap = new HashMap<>();
                linkMap.put("type", "button");
                linkMap.put("text", "Weiter");
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
