package ru.kochkaev.zixamc.requests

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.kochkaev.zixamc.api.Initializer
import ru.kochkaev.zixamc.api.ZixaMC
import ru.kochkaev.zixamc.api.config.ConfigManager
import ru.kochkaev.zixamc.api.sql.callback.CancelCallbackData
import ru.kochkaev.zixamc.api.telegram.BotLogic
import ru.kochkaev.zixamc.api.telegram.ServerBotGroup
import ru.kochkaev.zixamc.api.telegram.TelegramBotZixa

/**
 * @author kochkaev
 */
object RequestsBot {
    lateinit var bot: TelegramBotZixa
    val config
        get() = Config.config
    var isInitialized = false

    fun startBot() {
        bot = TelegramBotZixa(config.botAPIURL, config.botToken, ZixaMC.logger, config.pollTimeout)
        runBlocking {
            bot.init()
        }
        bot.registerMessageHandler(RequestsBotUpdateManager::onTelegramMessage)
        bot.registerNewChatMembersHandler(ServerBotGroup::newChatMembersRequests)

        bot.registerCallbackQueryHandler("cancel", CancelCallbackData::class.java) { cbq, sql -> CancelCallbackData.onCallback(cbq, sql, bot) }
        bot.registerCallbackQueryHandler("requests", RequestsBotUpdateManager.RequestCallback::class.java, RequestsBotUpdateManager::onTelegramCallbackQuery)

        bot.registerCommandHandler("accept", RequestsBotCommands::onTelegramAcceptCommand)
        bot.registerCommandHandler("reject", RequestsBotCommands::onTelegramRejectCommand)
        bot.registerCommandHandler("promote", RequestsBotCommands::onTelegramPromoteCommand)
        bot.registerCommandHandler("kick", RequestsBotCommands::onTelegramKickCommand)
        bot.registerCommandHandler("restrict", RequestsBotCommands::onTelegramRestrictCommand)
        bot.registerCommandHandler("leave", RequestsBotCommands::onTelegramLeaveCommand)
        bot.registerCommandHandler("return", RequestsBotCommands::onTelegramReturnCommand)
        bot.registerCommandHandler("start", RequestsBotCommands::onTelegramStartCommand)
        bot.registerCommandHandler("new", RequestsBotCommands::onTelegramNewCommand)
        bot.registerCommandHandler("cancel", RequestsBotCommands::onTelegramCancelCommand)

        Initializer.coroutineScope.launch {
            bot.startPosting(Initializer.coroutineScope)
            bot.startPolling(Initializer.coroutineScope)
//            ZixaMCTGBridge.isRequestsBotLoaded = true
        }
        isInitialized = true
    }
    fun stopBot() {
        Initializer.coroutineScope.launch {
            bot.shutdown()
        }
        isInitialized = false
    }
}