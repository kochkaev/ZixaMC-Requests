package ru.kochkaev.zixamc.requests

import ru.kochkaev.zixamc.api.formatLang
import ru.kochkaev.zixamc.api.sql.SQLCallback
import ru.kochkaev.zixamc.api.sql.SQLUser
import ru.kochkaev.zixamc.api.sql.data.AccountType
import ru.kochkaev.zixamc.api.sql.data.MinecraftAccountData
import ru.kochkaev.zixamc.requests.RequestsBot.bot
import ru.kochkaev.zixamc.requests.RequestsBot.config
import ru.kochkaev.zixamc.api.telegram.BotLogic
import ru.kochkaev.zixamc.api.telegram.model.*
import ru.kochkaev.zixamc.api.sql.SQLChat
import ru.kochkaev.zixamc.api.sql.SQLGroup
import ru.kochkaev.zixamc.api.sql.callback.TgMenu
import ru.kochkaev.zixamc.api.sql.chatdata.ChatDataTypes
import ru.kochkaev.zixamc.api.sql.data.MinecraftAccountType
import ru.kochkaev.zixamc.api.sql.feature.FeatureTypes

object RequestsLogic {

    suspend fun cancelRequest(user: SQLUser): Boolean {
        val request = user.data.getCasted(RequestsChatDataType)?.firstOrNull { RequestStatus.getAllPending().contains(it.status) } ?: return false
        RequestsChatDataType.editRequest(request.apply { this.status = RequestStatus.CANCELED }, user)
        bot.sendMessage(
            chatId = user.id,
            text = config.user.lang.event.onCanceled.formatLang("nickname" to (user.nickname?:"")),
            replyMarkup = TgMenu(listOf(listOf(
                SQLCallback.of(
                    display = config.user.lang.button.createRequest,
                    type = "requests",
                    data = RequestsBotUpdateManager.RequestCallback(RequestsBotUpdateManager.Operations.CREATE_REQUEST),
                )
            )))
        )
        if (request.inTargetMessageId != null) bot.sendMessage(
            chatId = config.target.chatId,
            messageThreadId = config.target.topicId,
            text = config.target.lang.event.onCanceled.formatLang("nickname" to (user.nickname?:"")),
            replyParameters = TgReplyParameters(
                message_id = request.inTargetMessageId!!.toInt()
            )
        )
        if (request.pollMessageId != null) bot.stopPoll(
            chatId = config.target.chatId,
            messageId = request.pollMessageId!!.toInt()
        )
        if (request.moderationMessageId != null) {
            bot.editMessageReplyMarkup(
                chatId = config.forModerator.chatId,
                messageId = request.moderationMessageId!!.toInt(),
                replyMarkup = TgReplyMarkup()
            )
            bot.editMessageText(
                chatId = config.forModerator.chatId,
                messageId = request.moderationMessageId!!.toInt(),
                text = config.forModerator.lang.event.onCancel.formatLang("nickname" to (request.nickname?:""))
            )
            SQLCallback.getAll(config.forModerator.chatId, request.moderationMessageId!!.toInt()).forEach { it.drop() }
        }
        user.tempArray.set(listOf())
        return true
    }
    suspend fun cancelSendingRequest(user: SQLUser): Boolean {
        val requests = user.data.getCasted(RequestsChatDataType)?:listOf()
        user.data.set(RequestsChatDataType, requests.filter { it.status == RequestStatus.CREATING })
        bot.sendMessage(
            chatId = user.id,
            text = config.user.lang.event.onCanceled,
            replyMarkup = TgMenu(listOf(listOf(
                SQLCallback.of(
                    display = config.user.lang.button.createRequest,
                    type = "requests",
                    data = RequestsBotUpdateManager.RequestCallback(RequestsBotUpdateManager.Operations.CREATE_REQUEST),
                )
            )))
        )
        return true
    }
    suspend fun newRequest(user: SQLUser): Boolean {
        when (user.data.getCasted(RequestsChatDataType)?.firstOrNull { RequestStatus.getAllPendingAndCreating().contains(it.status) }?.status) {
            RequestStatus.CREATING -> {
                bot.sendMessage(
                    chatId = user.id,
                    text = config.user.lang.creating.youAreNowCreatingRequest,
                    replyMarkup = TgMenu(listOf(listOf(
                        SQLCallback.of(
                            display = config.user.lang.button.redrawRequest,
                            type = "requests",
                            data = RequestsBotUpdateManager.RequestCallback(RequestsBotUpdateManager.Operations.REDRAW_REQUEST),
                        )
                    )))
                )
                return false
            }
            RequestStatus.MODERATING, RequestStatus.PENDING -> {
                bot.sendMessage(
                    chatId = user.id,
                    text = config.user.lang.creating.youHavePendingRequest.formatLang("nickname" to (user.nickname?:"")),
                    replyMarkup = TgMenu(listOf(listOf(
                        SQLCallback.of(
                            display = config.user.lang.button.cancelRequest,
                            type = "requests",
                            data = RequestsBotUpdateManager.RequestCallback(RequestsBotUpdateManager.Operations.CANCEL_REQUEST),
                        )
                    )))
                )
                return false
            }
            else -> {}
        }
        if (user.accountType.isPlayer) {
            bot.sendMessage(
                chatId = user.id,
                text = config.user.lang.creating.youAreNowPlayer.formatLang("nickname" to (user.nickname?:"")),
            )
            return false
        }
        val forReplyMessage = if (user.agreedWithRules) bot.sendMessage(
            chatId = user.id,
            text = config.user.lang.creating.needNickname,
            replyMarkup = TgForceReply(
                true,
                config.user.lang.inputField.enterNickname.ifEmpty { null }
            )
        )
        else bot.sendMessage(
            chatId = user.id,
            text = config.user.lang.creating.needAgreeWithRules,
            replyMarkup = TgMenu(listOf(listOf(
                SQLCallback.of(
                    display = config.user.lang.button.agreeWithRules,
                    type = "requests",
                    data = RequestsBotUpdateManager.RequestCallback(RequestsBotUpdateManager.Operations.AGREE_WITH_RULES),
                )
            )))
        )
        RequestsChatDataType.addRequest(
            requestData = RequestData(
                (user.data.getCasted(RequestsChatDataType)?.maxOfOrNull { it.requestId } ?: -1) + 1,
                null,
                forReplyMessage.messageId.toLong(),
                null,
                null,
                null,
                RequestStatus.CREATING,
                null,
            ),
            user = user
        )
        if (user.accountType == AccountType.UNKNOWN) user.accountType = AccountType.REQUESTER
        return true
    }

    fun promoteUser(argUser: SQLUser? = null, userId: Long? = null, nickname: String? = null, targetName: String? = null, argTargetId: Int? = null, argTarget: AccountType? = null): Boolean {
        val user = argUser ?:
            if (userId != null) SQLUser.get(userId) ?: return false
            else if (nickname != null) SQLUser.get(nickname) ?: return false
            else return false
        val target = argTarget ?:
            if (argTargetId!=null) AccountType.parse(argTargetId)
            else if (targetName!=null) AccountType.parse(targetName)
            else AccountType.UNKNOWN
        user.accountType = target
        return user.accountType==target
    }

    fun checkPermissionToExecute(
        message: TgMessage?,
        user: SQLUser = SQLUser.getOrCreate(message?.from!!.id),
        allowedAccountTypes: List<AccountType> = listOf(AccountType.ADMIN),
        allowedIfSpendByItself: Boolean = false,
    ): Boolean =
        (user.accountType in allowedAccountTypes || (allowedIfSpendByItself && message?.from?.id==user.id))

    fun matchEntityFromUpdateServerPlayerStatusCommand(msg: TgMessage?, allowedIfSpendByItself: Boolean = false): SQLUser? {
        if (msg == null) return null
        val args = msg.text!!.split(" ")
        val isArgUserId = if (args.size > 1) args[1].matches("[0-9]+".toRegex()) && args[1].length == 10 else false
        val isReplyToMessage = msg.replyToMessage != null
        val isItLegalReply = isReplyToMessage && msg.replyToMessage!!.messageId != config.target.topicId
        val user =
            if (isArgUserId)
                SQLUser.get(args[1].toLong())
            else if (isItLegalReply)
                SQLUser.get(msg.replyToMessage!!.from?.id ?: return null)
            else if (!isReplyToMessage && args.size>1 && args[1].matches("[a-zA-Z0-9_]+".toRegex()) && args[1].length in 3..16)
                SQLUser.get(args[1])
            else if (allowedIfSpendByItself)
                SQLUser.get(msg.from!!.id)
            else null
        return user
    }

    fun updateServerPlayerStatus(
        user: SQLUser,
        applyAccountStatuses: List<MinecraftAccountType> = MinecraftAccountType.getAll(),
        targetAccountStatus: MinecraftAccountType = MinecraftAccountType.PLAYER,
        targetAccountType: AccountType = targetAccountStatus.toAccountType(),
        editWhitelist: Boolean = false
    ) : Boolean {
        val isTargetPlayer = targetAccountType.isPlayer
        if (!promoteUser(
                argUser = user,
                argTarget = targetAccountType,
            )
        ) return false
        else {
            user.data.getCasted(ChatDataTypes.MINECRAFT_ACCOUNTS)
                ?.filter { applyAccountStatuses.contains(it.accountStatus) }
                ?.map { it.nickname }
                ?.forEach {
                    if (editWhitelist) {
                        if (isTargetPlayer) WhitelistManager.add(it)
                        else WhitelistManager.remove(it)
                    }
                    user.editMinecraftAccount(it, targetAccountStatus)
                }
            return true
        }
    }

    suspend fun sendOnJoinInfoMessage(
        chat: SQLChat,
        replyToMessageID: Int? = null,
    ) : TgMessage = BotLogic.sendInfoMessage(
            bot = bot,
            chat = chat,
            replyParameters = if (replyToMessageID!=null) TgReplyParameters(
                replyToMessageID
            ) else null,
            replyMarkup = TgInlineKeyboardMarkup(
                listOf(
                    listOf(
                        TgInlineKeyboardMarkup.TgInlineKeyboardButton(
                            text = config.user.lang.button.joinToPlayersGroup,
                            url = config.playersGroupInviteLink,
                        )
                    ),
                    listOf(BotLogic.copyIPReplyMarkup),
                )
            ),
        )

    suspend fun executeCheckPermissionsAndExceptions(
        message: TgMessage?,
        user: SQLUser?,
        executor: SQLUser? = if (message!=null) SQLUser.get(message.from!!.id) else null,
        allowedExecutionAccountTypes: List<AccountType> = listOf(AccountType.ADMIN),
        allowedExecutionIfSpendByItself: Boolean = false,
        applyAccountStatuses: List<MinecraftAccountType> = MinecraftAccountType.getAll(),
        targetAccountStatus: MinecraftAccountType = MinecraftAccountType.PLAYER,
        targetAccountType: AccountType = targetAccountStatus.toAccountType(),
        editWhitelist: Boolean = false,
        helpText: String? = null,
    ) : Boolean {
        var errorDueExecuting = false
        var havePermission = true
        if (executor == null || user == null) errorDueExecuting = true
        else havePermission = checkPermissionToExecute(
            message = message,
            user = executor,
            allowedAccountTypes = allowedExecutionAccountTypes,
            allowedIfSpendByItself = allowedExecutionIfSpendByItself,
        )
        if (!havePermission) errorDueExecuting = true
        if (!errorDueExecuting && !updateServerPlayerStatus(
                user = user!!,
                applyAccountStatuses = applyAccountStatuses,
                targetAccountStatus = targetAccountStatus,
                targetAccountType = targetAccountType,
                editWhitelist = editWhitelist,
            )
        ) errorDueExecuting = true
        if (errorDueExecuting && message!=null && helpText != null) {
            bot.sendMessage(
                chatId = message.chat.id,
                text = (if (!havePermission) config.commonLang.command.permissionDenied else helpText).formatLang("nickname" to (user?.nickname?:"")),
                replyParameters = TgReplyParameters(message.messageId),
            )
        }
        return errorDueExecuting
    }
    suspend fun executeRequestFinalAction(
        user: SQLUser,
        isAccepted: Boolean,
    ) : Boolean {
        val request = user.data.getCasted(RequestsChatDataType)?.firstOrNull {it.status == RequestStatus.PENDING} ?: return false
        val message4User = (if (isAccepted) config.user.lang.event.onAccept else config.user.lang.event.onReject).formatLang("nickname" to (request.nickname?:""))
        val message4Target = (if (isAccepted) config.target.lang.event.onAccept else config.target.lang.event.onReject).formatLang("nickname" to (request.nickname?:""))
        bot.sendMessage(
            chatId = config.target.chatId,
            text = message4Target,
            replyParameters = TgReplyParameters(request.pollMessageId!!.toInt()),
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
            sendOnJoinInfoMessage(user, newMessage.messageId)
            user.accountType = AccountType.PLAYER
            user.addMinecraftAccount(MinecraftAccountData(request.nickname!!, MinecraftAccountType.PLAYER))
            try { WhitelistManager.add(request.nickname!!) } catch (_:Exception) {}
            SQLGroup.getAllWithFeature(FeatureTypes.PLAYERS_GROUP)
                .filter { it.features.getCasted(FeatureTypes.PLAYERS_GROUP)?.autoRemove == true }
                .forEach { chat ->
                    for (bot in BotLogic.bots) try {
                        bot.unbanChatMember(
                            chatId = chat.id,
                            userId = user.id,
                            onlyIfBanned = true,
                        )
                    } catch (_: Exception) {}
                }
        }
        return true
    }
}