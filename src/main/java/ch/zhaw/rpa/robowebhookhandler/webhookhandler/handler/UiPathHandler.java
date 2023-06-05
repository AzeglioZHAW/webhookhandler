package ch.zhaw.rpa.robowebhookhandler.webhookhandler.handler;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2IntentMessage;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2IntentMessageCard;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2IntentMessageCardButton;
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
                        String intent, GoogleCloudDialogflowV2IntentMessage msg) throws InterruptedException {
                // ANPASSEN!!!!
                // Rechnungsnummer auslesen
                Object rechnungsnummerObject = request.getQueryResult().getParameters().get("Rechnungsnummer");
                String rechnungsnummer = rechnungsnummerObject != null ? rechnungsnummerObject.toString() : "";

                System.out.println("UiPathHandler: Rechnungsnummer In: "+rechnungsnummer);
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

                        try {
                                Thread.sleep(3000);
                        } catch (InterruptedException e) {
                                System.out.println("Der folgende Fehler ist aufgetreten: " + e.getLocalizedMessage()
                                                 + "Klicken Sie auf 'Weiter', wenn Sie es erneut versuchen möchten.");
                        }
                        
                        // Etwas Zeit "schinden", aber so, dass DialogFlow noch nicht abbricht und
                        if (sessionState.getUiPathJobState().equals("created")) {
                                // Text für Benutzer festlegen
                                msg = getResponseOfTypePleaseWait(
                                                "Es kann eine Minute dauern, bis die Informationen von der Originalquelle abgerufen werden. Klicken Sie auf 'Weiter', sobald Sie sehen möchten, ob die Informationen bereits vorhanden sind.",
                                                request, intent, msg);
                                // Damit er der Text direkt zum User kommt, die unteren Zeilen sind erst später relevant
                                return msg;
                        }
                }

                // Wenn ein zweiter, dritter, usw. Request vorhanden ist
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

                System.out.println("UiPathHandler msg: " + msg);

                return msg;
        }

        private GoogleCloudDialogflowV2IntentMessage getResponseOfTypePleaseWait(String promptText,
                        GoogleCloudDialogflowV2WebhookRequest request,
                        String intent, GoogleCloudDialogflowV2IntentMessage msg) {
                // Rich-Content-Payload in Form von verschachtelten HashMaps aufbereiten
                // basierend auf
                // https://cloud.google.com/dialogflow/es/docs/integrations/dialogflow-messenger?hl=en#rich

                GoogleCloudDialogflowV2IntentMessageCard card = new GoogleCloudDialogflowV2IntentMessageCard();
                GoogleCloudDialogflowV2IntentMessageCardButton cardButton = new GoogleCloudDialogflowV2IntentMessageCardButton();
                cardButton.setText("Weiter");
                cardButton.setPostback("Weiter");
                card.setButtons(List.of(cardButton));
                card.setTitle("Bitte warten...");
                card.setSubtitle(promptText);

                msg.setCard(card);

                return msg;
        }
}
