package ru.kochkaev.zixamc.requests

import ru.kochkaev.zixamc.api.formatLang
import ru.kochkaev.zixamc.api.sql.data.NewProtectedData
import ru.kochkaev.zixamc.api.sql.SQLUser
import ru.kochkaev.zixamc.api.sql.data.AccountType
import ru.kochkaev.zixamc.api.sql.data.MinecraftAccountData
import ru.kochkaev.zixamc.api.sql.data.MinecraftAccountType
import ru.kochkaev.zixamc.requests.RequestsBot.bot
import ru.kochkaev.zixamc.requests.RequestsBot.config
import ru.kochkaev.zixamc.api.telegram.model.TgMessage
import ru.kochkaev.zixamc.api.telegram.model.TgReplyMarkup
import ru.kochkaev.zixamc.api.telegram.model.TgReplyParameters

object RequestsCommandLogic {

    suspend fun executeUpdateServerPlayerStatusCommand(
        message: TgMessage?,
        allowedExecutionAccountTypes: List<AccountType> = listOf(AccountType.ADMIN),
        allowedExecutionIfSpendByItself: Boolean = false,
        applyAccountStatuses: List<MinecraftAccountType> = MinecraftAccountType.getAll(),
        targetAccountStatus: MinecraftAccountType = MinecraftAccountType.PLAYER,
        editWhitelist: Boolean = false,
        helpText: String? = null,
        text4User: String? = null,
        text4Target: String? = null,
        removePreviousTgReplyMarkup: Boolean = true,
        additionalConsumer: suspend (Boolean, SQLUser?) -> Unit = { _, _ -> },
        replyMarkup4Message: TgReplyMarkup? = null,
        protectContentInMessage: Boolean = false,
        removeProtectedContent: Boolean = false,
        user: SQLUser? = RequestsLogic.matchEntityFromUpdateServerPlayerStatusCommand(
            message,
            allowedExecutionIfSpendByItself
        ),
        executor: SQLUser? = if (message!=null) SQLUser.get(message.from!!.id) else null,
        messageForReplyId: Int? = message?.messageId,
    ) : Boolean {
        val errorDueExecuting = RequestsLogic.executeCheckPermissionsAndExceptions(
            message = message,
            user = user,
            executor = executor,
            allowedExecutionAccountTypes = allowedExecutionAccountTypes,
            allowedExecutionIfSpendByItself = allowedExecutionIfSpendByItself,
            applyAccountStatuses = applyAccountStatuses,
            targetAccountStatus = targetAccountStatus,
            editWhitelist = editWhitelist,
            helpText = helpText,
        )
        if (!errorDueExecuting) {
            if (text4Target!=null) bot.sendMessage(
                chatId = config.target.chatId,
                text = text4Target.formatLang("nickname" to (user?.nickname ?: user?.id.toString())),
                replyParameters = if (messageForReplyId!=null) TgReplyParameters(
                    messageForReplyId
                ) else null,
            )
            var newMessage: TgMessage? = null
            try {
                if (text4User!=null) {
                    newMessage = bot.sendMessage(
                        chatId = user!!.id,
                        text = text4User.formatLang("nickname" to (user.nickname ?: user.id.toString())),
                        replyMarkup = replyMarkup4Message,
                        protectContent = protectContentInMessage,
                    )
                    if (protectContentInMessage) user.setProtectedInfoMessage(
                        message = newMessage,
                        protectedType = NewProtectedData.ProtectedType.TEXT,
                        protectLevel = AccountType.PLAYER,
                        senderBotId = bot.me.id,
                    )
                }
            } catch (_: Exception) {}
            try {
                if (removePreviousTgReplyMarkup)
                    user!!.data.getCasted(RequestsChatDataType)?.filter { it.status == RequestStatus.ACCEPTED } ?.forEach {
                        bot.editMessageReplyMarkup(
                            chatId = user.id,
                            messageId = it.messageId.toInt(),
                            replyMarkup = TgReplyMarkup()
                        )
                    }
            } catch (_: Exception) {}
            if (removeProtectedContent)
                user!!.deleteProtected(targetAccountStatus.toAccountType())
            if (newMessage!=null)
                user!!.data.getCasted(RequestsChatDataType)?.filter { it.status == RequestStatus.ACCEPTED } ?.forEach {
//                    val request = it.copy(message_id_in_chat_with_user = newMessage.messageId.toLong())
//                    request.message_id_in_chat_with_user = newMessage.messageId.toLong()
                    RequestsChatDataType.editRequest(it.apply { this.messageId = newMessage.messageId.toLong() }, user)
                }
        }
        additionalConsumer.invoke(errorDueExecuting, user)
        return errorDueExecuting
    }

    suspend fun executeRequestFinalAction(
        message: TgMessage,
        isAccepted: Boolean,
    ) : Boolean {
        if (message.chat.id >= 0) return true
        val replied = message.replyToMessage?:return false
        val user = SQLUser.getByTempArray(replied.messageId.toString())?:return false
        if (!RequestsLogic.checkPermissionToExecute(
                message, user, listOf(AccountType.ADMIN), false
            )
        ) return true
        val request = user.data.getCasted(RequestsChatDataType)?.firstOrNull {it.status == RequestStatus.PENDING} ?: return false
        val message4User = (if (isAccepted) config.user.lang.event.onAccept else config.user.lang.event.onReject).formatLang("nickname" to (request.nickname?:""))
        val message4Target = (if (isAccepted) config.target.lang.event.onAccept else config.target.lang.event.onReject).formatLang("nickname" to (request.nickname?:""))
        bot.sendMessage(
            chatId = config.target.chatId,
            text = message4Target,
            replyParameters = TgReplyParameters(replied.messageId),
        )
        val newMessage = bot.sendMessage(
            chatId = user.id,
            text = message4User,
            replyParameters = TgReplyParameters(request.messageId.toInt()),
            protectContent = false,
        )
        bot.editMessageReplyMarkup(
            chatId = user.id,
            messageId = request.messageId.toInt(),
            replyMarkup = TgReplyMarkup()
        )
        request.status = if (isAccepted) RequestStatus.ACCEPTED else RequestStatus.REJECTED
        request.messageId = newMessage.messageId.toLong()
        RequestsChatDataType.editRequest(request, user)
        user.tempArray.set(listOf())
        if (isAccepted) {
            RequestsLogic.sendOnJoinInfoMessage(user, newMessage.messageId)
            user.accountType = AccountType.PLAYER
            user.addMinecraftAccount(MinecraftAccountData(request.nickname!!, MinecraftAccountType.PLAYER))
            WhitelistManager.add(request.nickname!!)
        }
        return true
    }
}