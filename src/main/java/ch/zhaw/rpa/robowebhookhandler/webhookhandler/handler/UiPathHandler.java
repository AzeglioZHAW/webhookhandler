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
                //ANPASSEN!!!!
                return msg;
    }
}
