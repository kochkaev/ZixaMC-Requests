package ru.kochkaev.zixamc.requests

import com.google.gson.annotations.SerializedName
import ru.kochkaev.zixamc.api.config.ConfigManager
import ru.kochkaev.zixamc.api.formatLang
import ru.kochkaev.zixamc.api.sql.SQLCallback
import ru.kochkaev.zixamc.requests.RequestsBot.bot
import ru.kochkaev.zixamc.requests.RequestsBot.config
import ru.kochkaev.zixamc.api.telegram.model.*
import ru.kochkaev.zixamc.api.sql.SQLUser
import ru.kochkaev.zixamc.api.sql.callback.CallbackData
import ru.kochkaev.zixamc.api.sql.callback.TgCBHandlerResult
import ru.kochkaev.zixamc.api.sql.callback.TgMenu
import ru.kochkaev.zixamc.api.sql.data.AccountType
import ru.kochkaev.zixamc.api.sql.data.MinecraftAccountType

object RequestsBotUpdateManager {
    suspend fun onTelegramMessage(msg: TgMessage) {
        if (msg.chat.id>=0) {
            val user = SQLUser.get(msg.from!!.id)?:return
            if (user.isRestricted) return
            if (user.accountType == AccountType.REQUESTER) {
                if (!user.agreedWithRules) {
                    bot.sendMessage(
                        msg.chat.id,
                        config.user.lang.creating.mustAgreeWithRules,
                    )
                    return
                }
                val requests = user.data.getCasted(RequestsChatDataType)?:listOf()
                val it = requests.first { !RequestStatus.getAllDone().contains(it.status) }
                when (it.status) {
                    RequestStatus.CREATING -> if (it.messageId == (msg.replyToMessage?.messageId?:return).toLong()) {
                        val newMessage: TgMessage
                        if (it.nickname == null) {
                            if ((msg.text?.length ?: return) !in 3..16 || !msg.text!!.matches(Regex("[a-zA-Z0-9_]+"))) {
                                newMessage = bot.sendMessage(
                                    chatId = msg.chat.id,
                                    text = config.user.lang.creating.wrongNickname.formatLang("nickname" to msg.text!!),
                                    replyParameters = TgReplyParameters(
                                        msg.messageId
                                    ),
                                    replyMarkup = TgForceReply(
                                        true,
                                        config.user.lang.inputField.enterNickname.ifEmpty { null }
                                    )
                                )
                            } else if (!user.canTakeNickname(msg.text!!)) {
                                newMessage = bot.sendMessage(
                                    chatId = msg.chat.id,
                                    text = config.user.lang.creating.takenNickname.formatLang("nickname" to msg.text!!),
                                    replyParameters = TgReplyParameters(
                                        msg.messageId
                                    ),
                                    replyMarkup = TgForceReply(
                                        true,
                                        config.user.lang.inputField.enterNickname.ifEmpty { null }
                                    )
                                )
                            } else {
                                newMessage = bot.sendMessage(
                                    chatId = msg.chat.id,
                                    text = config.user.lang.creating.needRequestText.formatLang("nickname" to msg.text!!),
                                    replyParameters = TgReplyParameters(
                                        msg.messageId
                                    ),
                                    replyMarkup = TgForceReply(
                                        true,
                                        config.user.lang.inputField.enterRequestText.ifEmpty { null }
                                    )
                                )
                                it.nickname = msg.text
                            }
                            it.messageId = newMessage.messageId.toLong()
                        }
                        else {
                            newMessage = bot.sendMessage(
                                chatId = msg.chat.id,
                                text = config.user.lang.creating.confirmSendRequest.formatLang("nickname" to (it.nickname?:"")),
                                replyParameters = TgReplyParameters(
                                    msg.messageId
                                ),
                                replyMarkup = TgMenu(listOf(listOf(
                                    SQLCallback.of(
                                        display = config.user.lang.button.confirmSending,
                                        type = "requests",
                                        data = RequestCallback(Operations.SEND_REQUEST),
                                    ),
                                    SQLCallback.of(
                                        display = config.user.lang.button.cancelRequest,
                                        type = "requests",
                                        data = RequestCallback(Operations.CANCEL_SENDING_REQUEST),
                                    ),
                                )))
                            )
                            it.messageId = msg.messageId.toLong()
                        }
                        RequestsChatDataType.editRequest(it, user)
//                        bot.editMessageReplyMarkup(
//                            chatId = msg.chat.id,
//                            messageId = msg.replyToMessage.messageId,
//                            replyMarkup = TgReplyMarkup()
//                        )
                    }
                    RequestStatus.PENDING -> {
//                        val firstReply = msg.replyToMessage?:return
//                        if (firstReply.from?.id == bot.me.id && firstReply.forwardOrigin != null) {
//                            val forwardedMessage = bot.forwardMessage(
//                                chatId = config.targetChatId,
//                                messageThreadId = config.targetTopicId,
//                                fromChatId = msg.chat.id,
//                                messageId = msg.messageId,
//                            )
//                            entity.addToTempArray(forwardedMessage.messageId.toString())
//                        }
                        val forwardedMessage = bot.forwardMessage(
                            chatId = config.target.chatId,
                            messageThreadId = config.target.topicId,
                            fromChatId = msg.chat.id,
                            messageId = msg.messageId,
                        )
                        user.tempArray.add(forwardedMessage.messageId.toString())
                    }
                    else -> {}
                }
            }
        }
        else {
            val replied = msg.replyToMessage?:return
            val user = SQLUser.getByTempArray(replied.messageId.toString())?:return
            if (!user.tempArray.contains(replied.messageId.toString()) || !(user.data.getCasted(RequestsChatDataType)?:listOf()).any { RequestStatus.getAllPending().contains(it.status)}) return
            bot.forwardMessage(
                chatId = user.id,
                fromChatId = msg.chat.id,
                messageId = msg.messageId,
            )
            user.tempArray.add(msg.messageId.toString())
        }
    }
    suspend fun onTelegramCallbackQuery(cbq: TgCallbackQuery, sql: SQLCallback<RequestCallback>): TgCBHandlerResult {
        val user = SQLUser.get(cbq.from.id)?:return TgCBHandlerResult.SUCCESS
        if (user.isRestricted) return TgCBHandlerResult.DELETE_MARKUP
        when (sql.data?.operation) {
            Operations.AGREE_WITH_RULES -> {
                user.agreedWithRules = true
                val requests = user.data.getCasted(RequestsChatDataType)?:listOf()
                if (requests.any {it.status == RequestStatus.CREATING}) {
                    val editedRequest = requests.first{it.status == RequestStatus.CREATING}
                    val newMessage = bot.sendMessage(
                        chatId = cbq.from.id,
                        text = config.user.lang.creating.needNickname,
                        replyMarkup = TgForceReply(
                            true,
                            config.user.lang.inputField.enterNickname.ifEmpty { null }
                        )
                    )
                    editedRequest.messageId = newMessage.messageId.toLong()
                    RequestsChatDataType.editRequest(editedRequest, user)
                }
            }
            Operations.REVOKE_AGREE_WITH_RULES -> {
                bot.sendMessage(
                    chatId = cbq.message.chat.id,
                    text = ConfigManager.config.general.rules.confirmRemoveAgree4player.formatLang("nickname" to (user.nickname?:cbq.from.firstName)),
                    replyMarkup = TgMenu(listOf(
                        listOf(SQLCallback.of(
                            display = ConfigManager.config.general.buttons.confirm,
                            type = "requests",
                            data = RequestCallback(Operations.CONFIRM_REVOKE_AGREE_WITH_RULES, user.id)
                        )),
                        listOf(SQLCallback.of(
                            display = ConfigManager.config.general.buttons.cancel,
                            type = "requests",
                            data = RequestCallback(Operations.SUCCESS, user.id)
                        )),
                    ))
                )
                return TgCBHandlerResult.SUCCESS
            }
            Operations.CONFIRM_REVOKE_AGREE_WITH_RULES -> {
                if (sql.data?.userId != user.id) {
                    bot.answerCallbackQuery(
                        callbackQueryId = cbq.id,
                        text = ConfigManager.config.general.rules.thatButtonFor.formatLang(
                            "nickname" to (sql.data?.userId?.let { SQLUser.get(it)?.nickname ?: it.toString() } ?:"")
                        ),
                        showAlert = true,
                    )
                    return TgCBHandlerResult.SUCCESS
                }
                user.agreedWithRules = false
                RequestsCommandLogic.executeUpdateServerPlayerStatusCommand(
                    message = null,
                    allowedExecutionAccountTypes = AccountType.entries,
                    allowedExecutionIfSpendByItself = true,
                    applyAccountStatuses = MinecraftAccountType.getAllActiveNow(),
                    targetAccountStatus = MinecraftAccountType.FROZEN,
                    editWhitelist = true,
                    helpText = null,
                    text4User = ConfigManager.config.general.rules.onLeave4player,
                    text4Target = ConfigManager.config.general.rules.onLeave4group,
                    removePreviousTgReplyMarkup = true,
                    removeProtectedContent = true,
                    user = user,
                    executor = user,
                    messageForReplyId = cbq.message.messageId,
                )
                return TgCBHandlerResult.DELETE_MARKUP
            }
            Operations.REDRAW_REQUEST -> {
                val requests = user.data.getCasted(RequestsChatDataType)?:listOf()
                user.data.set(RequestsChatDataType, requests.filter { it1 -> it1.status != RequestStatus.CREATING })
                RequestsLogic.newRequest(user)
            }
            Operations.CANCEL_REQUEST -> RequestsLogic.cancelRequest(user)
            Operations.CANCEL_SENDING_REQUEST -> RequestsLogic.cancelSendingRequest(user)
            Operations.CREATE_REQUEST -> RequestsLogic.newRequest(user)
            Operations.SEND_REQUEST -> {
                val request = (user.data.getCasted(RequestsChatDataType)?:listOf()).first { it.status == RequestStatus.CREATING }
                val forwarded = bot.forwardMessage(
                    chatId = config.forModerator.chatId,
                    messageThreadId = config.forModerator.topicId,
                    fromChatId = user.id,
                    messageId = request.messageId.toInt()
                )
                val messageInChatWithUser = bot.sendMessage(
                    chatId = user.id,
                    text = config.user.lang.event.onSend.formatLang("nickname" to (request.nickname?:"")),
                    replyParameters = TgReplyParameters(cbq.message.messageId),
                    replyMarkup = TgMenu(listOf(listOf(
                        SQLCallback.of(
                            display = config.user.lang.button.cancelRequest,
                            type = "requests",
                            data = RequestCallback(Operations.CANCEL_REQUEST),
                        )
                    )))
                )
                val moderatorsControl = bot.sendMessage(
                    chatId = config.forModerator.chatId,
                    messageThreadId = config.forModerator.topicId,
                    text = config.forModerator.lang.event.onNew.formatLang("nickname" to (request.nickname?:"")),
                    replyMarkup = TgMenu(listOf(
                        listOf(
                            SQLCallback.of(
                                display = config.forModerator.lang.button.approveSending,
                                type = "requests",
                                data = RequestCallback(Operations.APPROVE_REQUEST),
                            ),
                            SQLCallback.of(
                                display = config.forModerator.lang.button.denySending,
                                type = "requests",
                                data = RequestCallback(Operations.DENY_REQUEST),
                            ),
                        ),
                        listOf(SQLCallback.of(
                            display = config.forModerator.lang.button.restrictSender,
                            type = "requests",
                            data = RequestCallback(Operations.RESTRICT_USER),
                        )),
                    )),
                    replyParameters = TgReplyParameters(forwarded.messageId),
                    protectContent = true,
                )
                request.requestMessageId = request.messageId
                request.messageId = messageInChatWithUser.messageId.toLong()
                request.moderationMessageId = moderatorsControl.messageId.toLong()
                request.status = RequestStatus.MODERATING
                RequestsChatDataType.editRequest(request, user)
                user.addNickname(request.nickname!!)
            }
            Operations.APPROVE_REQUEST -> {
                val requester = SQLUser.users.first {
                    it.data.getCasted(RequestsChatDataType)?.any { it1 -> it1.status == RequestStatus.MODERATING && it1.moderationMessageId?.toInt() == cbq.message.messageId } == true
                }
                val request = requester.data.getCasted(RequestsChatDataType)!!.first { it.status == RequestStatus.MODERATING }
                val forwardedMessage = bot.forwardMessage(
                    chatId = config.target.chatId,
                    messageThreadId = config.target.topicId,
                    fromChatId = cbq.message.chat.id,
                    messageId = cbq.message.replyToMessage?.messageId?:return TgCBHandlerResult.SUCCESS
                )
                val newMessage = bot.sendMessage(
                    chatId = config.target.chatId,
                    text = config.target.lang.event.onSend.formatLang("nickname" to (request.nickname?:"")),
                    replyParameters = TgReplyParameters(
                        forwardedMessage.messageId
                    ),
                )
                val poll = bot.sendPoll(
                    chatId = config.target.chatId,
                    messageThreadId = config.target.topicId,
                    question = config.target.lang.poll.question.formatLang("nickname" to (request.nickname?:"")),
                    options = listOf(
                        TgInputPollOption(config.target.lang.poll.answerTrue),
                        TgInputPollOption(config.target.lang.poll.answerNull),
                        TgInputPollOption(config.target.lang.poll.answerFalse),
                    ),
                    replyParameters = TgReplyParameters(
                        message_id = forwardedMessage.messageId,
                    ),
                )
                requester.tempArray.add(poll.messageId.toString())
                bot.pinMessage(config.target.chatId, forwardedMessage.messageId.toLong(), true)
                bot.editMessageReplyMarkup(
                    chatId = requester.id,
                    messageId = request.messageId.toInt(),
                    replyMarkup = TgReplyMarkup()
                )
                SQLCallback.getAll(requester.id, request.messageId.toInt()).forEach { it.drop() }
                val messageInChatWithUser = bot.sendMessage(
                    chatId = requester.id,
                    text = config.user.lang.event.onApprove.formatLang("nickname" to (request.nickname?:"")),
                    replyParameters = TgReplyParameters(request.messageId.toInt()),
                    replyMarkup = TgMenu(listOf(listOf(
                        SQLCallback.of(
                            display = config.user.lang.button.cancelRequest,
                            type = "requests",
                            data = RequestCallback(Operations.CANCEL_REQUEST),
                        )
                    )))
                )
                val moderatorsControl = bot.editMessageText(
                    chatId = cbq.message.chat.id,
                    messageId = cbq.message.messageId,
                    text = config.forModerator.lang.event.onApprove.formatLang("nickname" to (request.nickname?:"")),
                )
                SQLCallback.getAll(moderatorsControl.chat.id, moderatorsControl.messageId).forEach { it.drop() }
                bot.editMessageReplyMarkup(
                    chatId = moderatorsControl.chat.id,
                    messageId = moderatorsControl.messageId,
                    replyMarkup = TgMenu(listOf(
                        listOf(SQLCallback.of(
                            display = config.forModerator.lang.button.closeRequestVote,
                            type = "requests",
                            data = RequestCallback(Operations.CLOSE_POLL)
                        )),
                        listOf(SQLCallback.of(
                            display = config.forModerator.lang.button.restrictSender,
                            type = "requests",
                            data = RequestCallback(Operations.RESTRICT_USER)
                        )),
                    ))
                )
                request.messageId = messageInChatWithUser.messageId.toLong()
                request.inTargetMessageId = forwardedMessage.messageId.toLong()
                request.pollMessageId = poll.messageId.toLong()
                requester.tempArray.add(forwardedMessage.messageId.toString())
                requester.tempArray.add(newMessage.messageId.toString())
                request.status = RequestStatus.PENDING
                RequestsChatDataType.editRequest(request, requester)
            }
            Operations.DENY_REQUEST -> {
                val requester = SQLUser.users.first {
                    it.data.getCasted(RequestsChatDataType)?.any { it1 -> it1.status == RequestStatus.MODERATING && it1.moderationMessageId?.toInt() == cbq.message.messageId } == true
                }
                val request = requester.data.getCasted(RequestsChatDataType)!!.first { it.status == RequestStatus.MODERATING }
                bot.editMessageReplyMarkup(
                    chatId = requester.id,
                    messageId = request.messageId.toInt(),
                    replyMarkup = TgReplyMarkup()
                )
                SQLCallback.getAll(requester.id, request.messageId.toInt()).forEach { it.drop() }
                val messageInChatWithUser = bot.sendMessage(
                    chatId = requester.id,
                    text = config.user.lang.event.onDeny.formatLang("nickname" to (request.nickname?:"")),
                    replyParameters = TgReplyParameters(request.messageId.toInt()),
                    replyMarkup = TgMenu(listOf(listOf(
                        SQLCallback.of(
                            display = config.user.lang.button.redrawRequest,
                            type = "requests",
                            data = RequestCallback(Operations.REDRAW_REQUEST),
                        )
                    )))
                )
                bot.editMessageText(
                    chatId = cbq.message.chat.id,
                    messageId = cbq.message.messageId,
                    text = config.forModerator.lang.event.onDeny.formatLang("nickname" to (request.nickname?:"")),
                )
                request.messageId = messageInChatWithUser.messageId.toLong()
                request.status = RequestStatus.DENIED
                RequestsChatDataType.editRequest(request, requester)
            }
            Operations.RESTRICT_USER -> {
                val requester = SQLUser.users.first {
                    it.data.getCasted(RequestsChatDataType)?.any { it1 -> it1.status == RequestStatus.MODERATING && it1.moderationMessageId?.toInt() == cbq.message.messageId } == true
                }
                val request = requester.data.getCasted(RequestsChatDataType)!!.first { it.status == RequestStatus.MODERATING }
                bot.editMessageText(
                    chatId = cbq.message.chat.id,
                    messageId = cbq.message.messageId,
                    text = config.user.lang.event.onRestrict.formatLang("nickname" to (request.nickname?:""))
                )
                try {
                    val text4User = config.user.lang.event.onRestrict
                    if (text4User.isNotEmpty()) {
                        bot.sendMessage(
                            chatId = requester.id,
                            text = text4User.formatLang("nickname" to (user.nickname ?: user.id.toString())),
                        )
                    }
                } catch (_: Exception) {}
                requester.deleteProtected(AccountType.UNKNOWN)
                request.status = RequestStatus.DENIED
                RequestsChatDataType.editRequest(request, requester)
                requester.isRestricted = true
            }
            Operations.CLOSE_POLL -> {
                if (user.accountType != AccountType.ADMIN) return TgCBHandlerResult.SUCCESS
                SQLCallback.getAll(cbq.message.chat.id, cbq.message.messageId).forEach { it.drop() }
                val userEntity = SQLUser.users.first {
                    it.data.getCasted(RequestsChatDataType)?.any { it1 -> it1.status == RequestStatus.PENDING && it1.moderationMessageId?.toInt() == cbq.message.messageId } == true
                }
                val request = userEntity.data.getCasted(RequestsChatDataType)!!.first { it.status == RequestStatus.PENDING }
                SQLCallback.getAll(userEntity.id, request.messageId.toInt()).forEach { it.drop() }
                val isAccepted = bot.stopPoll(
                    chatId = config.target.chatId,
                    messageId = request.pollMessageId?.toInt()?:return TgCBHandlerResult.SUCCESS
                ).options
                    ?.filter { it.text != config.target.lang.poll.answerNull }
                    ?.maxBy { it.voter_count } ?.text?.equals(config.target.lang.poll.answerTrue) ?: false
                RequestsLogic.executeRequestFinalAction(userEntity, isAccepted)
                bot.editMessageText(
                    chatId = config.forModerator.chatId,
                    messageId = request.moderationMessageId!!.toInt(),
                    text = config.forModerator.lang.event.onVoteClosed.formatLang("nickname" to (request.nickname?:"")),
                )
            }
            Operations.SUCCESS -> {
                if (sql.data?.userId != user.id) {
                    bot.answerCallbackQuery(
                        callbackQueryId = cbq.id,
                        text = ConfigManager.config.general.rules.thatButtonFor.formatLang(
                            "nickname" to (sql.data?.userId?.let { SQLUser.get(it)?.nickname ?: it.toString() } ?:"")
                        ),
                        showAlert = true,
                    )
                    return TgCBHandlerResult.SUCCESS
                }
                return TgCBHandlerResult.DELETE_MESSAGE
            }
            else -> {}
        }
        return if (cbq.message.chat.id>0) TgCBHandlerResult.DELETE_MARKUP else TgCBHandlerResult.SUCCESS
    }

    open class RequestCallback(
        var operation: Operations,
        var userId: Long? = null,
    ): CallbackData
    enum class Operations {
        @SerializedName("agree_with_rules")
        AGREE_WITH_RULES,
        @SerializedName("revoke_agree_with_rules")
        REVOKE_AGREE_WITH_RULES,
        @SerializedName("confirm_revoke_agree_with_rules")
        CONFIRM_REVOKE_AGREE_WITH_RULES,
        @SerializedName("redraw_request")
        REDRAW_REQUEST,
        @SerializedName("cancel_request")
        CANCEL_REQUEST,
        @SerializedName("cancel_sending_request")
        CANCEL_SENDING_REQUEST,
        @SerializedName("create_request")
        CREATE_REQUEST,
        @SerializedName("send_request")
        SEND_REQUEST,
        @SerializedName("approve_request")
        APPROVE_REQUEST,
        @SerializedName("deny_request")
        DENY_REQUEST,
        @SerializedName("restrict_user")
        RESTRICT_USER,
        @SerializedName("close_poll")
        CLOSE_POLL,
        @SerializedName("success")
        SUCCESS,
    }
}