package ru.kochkaev.zixamc.requests

import com.google.gson.annotations.SerializedName

data class RequestData(
    /** Local for user request id. */
    @SerializedName("user_request_id")
    var requestId: Int,
    /** ID of message that forwarded to target chat from chat with user (or to moderators if it enabled). */
    @SerializedName("message_id_in_target_chat")
    var inTargetMessageId: Long?,
    /** Temp message id. */
    @SerializedName("message_id_in_chat_with_user")
    var messageId: Long,
    /** Message id of request text in chat with user. Message with that id forwards to moderators and target chat. */
    @SerializedName("request_message_id_in_chat_with_user")
    var requestMessageId: Long?,
    /** ID of message that forwarded to moderators chat from chat with user. */
    @SerializedName("message_id_in_moderators_chat")
    var moderationMessageId: Long?,
    /** Message id of auto-created poll in target chat. */
    @SerializedName("poll_message_id")
    var pollMessageId: Long?,
    /** Status of this request. At the same time only one request with status "CREATING", "MODERATING", or "PENDING" can exist for one user. */
    @SerializedName("request_status")
    var status: RequestStatus,
    /** Nickname that user specified when creating request. */
    @SerializedName("request_nickname")
    var nickname: String?,
)
