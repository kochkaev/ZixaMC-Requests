package ru.kochkaev.zixamc.requests

import ru.kochkaev.zixamc.api.config.ConfigManager
import ru.kochkaev.zixamc.api.formatLang
import ru.kochkaev.zixamc.api.sql.SQLCallback
import ru.kochkaev.zixamc.requests.RequestsBot.bot
import ru.kochkaev.zixamc.requests.RequestsBot.config
import ru.kochkaev.zixamc.api.sql.data.AccountType
import ru.kochkaev.zixamc.api.sql.data.MinecraftAccountType
import ru.kochkaev.zixamc.api.telegram.model.TgMessage
import ru.kochkaev.zixamc.requests.RequestsLogic.matchEntityFromUpdateServerPlayerStatusCommand
import ru.kochkaev.zixamc.api.sql.SQLUser
import ru.kochkaev.zixamc.api.sql.SQLGroup
import ru.kochkaev.zixamc.api.sql.callback.CallbackCanExecute
import ru.kochkaev.zixamc.api.sql.callback.TgMenu
import ru.kochkaev.zixamc.api.telegram.ServerBot
import ru.kochkaev.zixamc.api.telegram.model.TgInlineKeyboardMarkup
import ru.kochkaev.zixamc.api.telegram.model.TgReplyMarkup
import ru.kochkaev.zixamc.api.telegram.model.TgReplyParameters
import ru.kochkaev.zixamc.api.sql.feature.FeatureTypes

object RequestsBotCommands {
    suspend fun onTelegramAcceptCommand(msg: TgMessage): Boolean {
//        RequestsCommandLogic.executeRequestFinalAction(msg, true)
        return RequestsLogic.executeRequestFinalAction(
            user = SQLUser.get(msg.from?.id ?: return false) ?: return false,
            isAccepted = true,
        )
    }
    suspend fun onTelegramRejectCommand(msg: TgMessage): Boolean {
//        RequestsCommandLogic.executeRequestFinalAction(msg, false)
        return RequestsLogic.executeRequestFinalAction(
            user = SQLUser.get(msg.from?.id ?: return false) ?: return false,
            isAccepted = false,
        )
    }
    suspend fun onTelegramPromoteCommand(msg: TgMessage): Boolean {
        val user = matchEntityFromUpdateServerPlayerStatusCommand(msg) ?:return false
        if (!RequestsLogic.checkPermissionToExecute(
                msg, user, listOf(AccountType.ADMIN), false
            )) return true
        if (!RequestsLogic.promoteUser(user)) {
            bot.sendMessage(
                chatId = msg.chat.id,
                text = config.commonLang.command.promoteHelp,
                replyParameters = TgReplyParameters(msg.messageId),
            )
            return false
        } else {
            bot.sendMessage(
                chatId = msg.chat.id,
                text = config.target.lang.event.onPromote.formatLang("nickname" to (user.nickname?:user.id.toString())),
                replyParameters = TgReplyParameters(msg.messageId),
            )
            return true
        }
    }
    suspend fun onTelegramLeaveCommand(msg: TgMessage): Boolean =
        RequestsCommandLogic.executeUpdateServerPlayerStatusCommand(
            message = msg,
            allowedExecutionAccountTypes = listOf(AccountType.ADMIN),
            allowedExecutionIfSpendByItself = true,
            applyAccountStatuses = MinecraftAccountType.getAllActiveNow(),
            targetAccountStatus = MinecraftAccountType.FROZEN,
            editWhitelist = true,
            helpText = config.commonLang.command.leaveHelp,
            text4User = ConfigManager.config.general.rules.onLeave4player,
            text4Target = ConfigManager.config.general.rules.onLeave4group,
            removePreviousTgReplyMarkup = true,
//            additionalConsumer = { hasError, entity ->
//                if (!hasError) try {
//                    bot.banChatMember(msg.chat.id, entity!!.userId)
//                } catch (_: Exception) {}
//            },
            removeProtectedContent = true,
        )
    suspend fun onTelegramReturnCommand(msg: TgMessage): Boolean =
        RequestsCommandLogic.executeUpdateServerPlayerStatusCommand(
            message = msg,
            allowedExecutionAccountTypes = listOf(AccountType.ADMIN),
            allowedExecutionIfSpendByItself = false,
            applyAccountStatuses = listOf(MinecraftAccountType.FROZEN),
            targetAccountStatus = MinecraftAccountType.PLAYER,
            editWhitelist = true,
            helpText = config.commonLang.command.returnHelp,
            text4User = config.user.lang.event.onReturn,
            text4Target = config.target.lang.event.onReturn,
            removePreviousTgReplyMarkup = true,
            replyMarkup4Message = TgInlineKeyboardMarkup(
                listOf(
                    listOf(
                        TgInlineKeyboardMarkup.TgInlineKeyboardButton(
                            text = config.user.lang.button.joinToPlayersGroup,
                            url = config.playersGroupInviteLink
                        )
                    )
                )
            ),
            additionalConsumer = { hasError, entity ->
                if (!hasError) SQLGroup.getAllWithFeature(FeatureTypes.PLAYERS_GROUP).forEach {
                    try {
                        bot.unbanChatMember(it.id, entity!!.id, true)
                    } catch (_: Exception) { try {
                        ServerBot.bot.unbanChatMember(it.id, entity!!.id, true)
                    } catch (_: Exception) {} }
                }
            },
            protectContentInMessage = true,
        )
    suspend fun onTelegramKickCommand(msg: TgMessage): Boolean =
        RequestsCommandLogic.executeUpdateServerPlayerStatusCommand(
            message = msg,
            allowedExecutionAccountTypes = listOf(AccountType.ADMIN),
            allowedExecutionIfSpendByItself = false,
            applyAccountStatuses = MinecraftAccountType.getAllMaybeActive(),
            targetAccountStatus = MinecraftAccountType.BANNED,
            editWhitelist = true,
            helpText = config.commonLang.command.kickHelp,
            text4User = config.user.lang.event.onKick,
            text4Target = config.target.lang.event.onKick,
            removePreviousTgReplyMarkup = true,
//            additionalConsumer = { hasError, entity ->
//                if (!hasError) try {
//                    bot.banChatMember(msg.chat.id, entity!!.userId)
//                } catch (_: Exception) {}
//            },
            removeProtectedContent = true,
        )
    suspend fun onTelegramRestrictCommand(message: TgMessage): Boolean {
        val user = SQLUser.getByTempArray(message.replyToMessage?.messageId.toString())
            ?: RequestsLogic.matchEntityFromUpdateServerPlayerStatusCommand(message, false)
        val errorDueExecuting = RequestsLogic.executeCheckPermissionsAndExceptions(
            message = message,
            user = user,
            allowedExecutionAccountTypes = listOf(AccountType.ADMIN),
            allowedExecutionIfSpendByItself = false,
            applyAccountStatuses = MinecraftAccountType.getAllMaybeActive(),
            targetAccountStatus = MinecraftAccountType.BANNED,
            targetAccountType = AccountType.UNKNOWN,
            editWhitelist = true,
            helpText = config.commonLang.command.restrictHelp,
        )
        if (!errorDueExecuting) {
            val text4Target = config.target.lang.event.onRestrict
            if (text4Target.isNotEmpty()) bot.sendMessage(
                chatId = message.chat.id,
                text = text4Target.formatLang("nickname" to (user!!.nickname ?: user.id.toString())),
                replyParameters = TgReplyParameters(message.messageId),
            )
            var newMessage: TgMessage? = null
            try {
                val text4User = config.user.lang.event.onRestrict
                if (text4User.isNotEmpty()) {
                    newMessage = bot.sendMessage(
                        chatId = user!!.id,
                        text = text4User.formatLang("nickname" to (user.nickname ?: user.id.toString())),
                    )
                }
            } catch (_: Exception) {}
            user!!.deleteProtected(AccountType.UNKNOWN)
            val requests = user.data.getCasted(RequestsChatDataType)?:listOf()
            try {
                requests.filter { it.status == RequestStatus.ACCEPTED } .forEach {
                    bot.editMessageReplyMarkup(
                        chatId = user.id,
                        messageId = it.messageId.toInt(),
                        replyMarkup = TgReplyMarkup()
                    )
                }
            } catch (_: Exception) {}
            user.data.set(RequestsChatDataType, requests.filter { it1 -> it1.status != RequestStatus.CREATING })
            requests.firstOrNull { RequestStatus.getAllPending().contains(it.status) } ?.let {
                RequestsChatDataType.editRequest(it.apply { this.status = RequestStatus.REJECTED }, user)
            }
            if (newMessage!=null)
                requests.filter { it.status == RequestStatus.ACCEPTED } .forEach {
                    RequestsChatDataType.editRequest(it.apply { this.messageId = newMessage.messageId.toLong() }, user)
                }
//            try {
//                bot.banChatMember(message.chat.id, entity.userId)
//            } catch (_: Exception) {}
            user.isRestricted = true
        }
        return errorDueExecuting
    }
    suspend fun onTelegramStartCommand(msg: TgMessage): Boolean {
        if (msg.chat.id < 0) return true
        val user = SQLUser.getOrCreate(msg.from?.id?:return false)
        if (user.isRestricted) return false
        bot.sendMessage(
            chatId = msg.chat.id,
            text = config.user.lang.event.onStart,
            replyMarkup = TgMenu(listOf(listOf(
                SQLCallback.of(
                    display = config.user.lang.button.createRequest,
                    type = "requests",
                    data = RequestsBotUpdateManager.RequestCallback(RequestsBotUpdateManager.Operations.CREATE_REQUEST),
                    canExecute = CallbackCanExecute(
                        statuses = null,
                        users = listOf(user.id),
                        display = "",
                    ),
                )
            )))
        )
        return true
    }
    suspend fun onTelegramNewCommand(msg: TgMessage): Boolean {
        if (msg.chat.id < 0) return true
        if (msg.from == null) return false
        val user = SQLUser.get(msg.from?.id?:return false)?:return false
        if (user.isRestricted) return false
        return RequestsLogic.newRequest(user)
    }
    suspend fun onTelegramCancelCommand(msg: TgMessage): Boolean {
        if (msg.chat.id < 0) return true
        val user = SQLUser.get(msg.from?.id?:return false)?:return false
        val requests = user.data.getCasted(RequestsChatDataType)?:listOf()
        if (requests.any { RequestStatus.getAllPending().contains(it.status)}) return RequestsLogic.cancelRequest(
            user
        )
        else if (requests.any {it.status == RequestStatus.CREATING}) return RequestsLogic.cancelSendingRequest(
            user
        )
        return false
    }
}