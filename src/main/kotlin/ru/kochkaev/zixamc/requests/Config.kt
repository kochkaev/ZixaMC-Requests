package ru.kochkaev.zixamc.requests

import net.fabricmc.loader.api.FabricLoader
import ru.kochkaev.zixamc.api.config.ConfigFile
import java.io.File

data class Config(
    val botToken: String = "",
    val botAPIURL: String = "https://api.telegram.org",
    val pollTimeout: Int = 60,
    val playersGroupInviteLink: String = "https://t.me/",
    val user: RequestsBotForUser = RequestsBotForUser(),
    val target: RequestsBotForTarget = RequestsBotForTarget(),
    val forModerator: RequestsBotForModerator = RequestsBotForModerator(),
    val commonLang: RequestsBotCommonLang = RequestsBotCommonLang(),
) {
    data class RequestsBotForUser (
        val lang: RequestsBotForUserLang = RequestsBotForUserLang(),
    ) {
        data class RequestsBotForUserLang (
            val button: RequestsBotForUserLangButtons = RequestsBotForUserLangButtons(),
            val inputField: RequestsBotForUserLangInputFields = RequestsBotForUserLangInputFields(),
            val event: RequestsBotForUserLangEvents = RequestsBotForUserLangEvents(),
            val creating: RequestsBotForUserLangCreating = RequestsBotForUserLangCreating(),
        ) {
            data class RequestsBotForUserLangButtons (
                val createRequest: String = "Создать заявку ⚡",
                val confirmSending: String = "Отправить заявку 🚀",
                val agreeWithRules: String = "С правилами ознакомлен и согласен ✅",
                val revokeAgreeWithRules: String = "Отозвать согласие с правилами ⛔",
                val redrawRequest: String = "Начать заново 📝",
                val cancelRequest: String = "Отменить заявку ❌",
                val joinToPlayersGroup: String = "Присоедениться к группе игроков ✈️",
            )
            data class RequestsBotForUserLangInputFields (
                val enterNickname: String = "Введите никнейм...",
                val enterRequestText: String = "Напишите заявку...",
            )
            data class RequestsBotForUserLangEvents (
                val onStart: String = "<b>Приветствую! <tg-emoji emoji-id=\"5462910521739063094\">👋</tg-emoji></b>\n\nХотели стать игроком приватного Minecraft сервера Zixa City? Вы пришли по адресу! <tg-emoji emoji-id=\"5285291543622601498\">👍</tg-emoji>\n\nДля того, что бы отправить свою заявку, нажмите на кнопку <tg-emoji emoji-id=\"5197474438970363734\">⤵️</tg-emoji>",
                val onSend: String = "<b>Заявка успешно отправлена на модерацию администрации! <tg-emoji emoji-id=\"5258203794772085854\">⚡️</tg-emoji></b>",
                val onApprove: String = "<b>Заявка успешно прошла модерацию и уже отправлена игрокам! 🎉</b>",
                val onDeny: String = "<b>К сожалению, ваша заявка не прошла модерацию. <tg-emoji emoji-id=\"5197279271361456513\">😞</tg-emoji></b>\nСоветуем ещё раз ознакомиться с <a href=\"https://teletype.in/@zixamc/rules-general\">правилами сервера</a>.\n\nХотите создать новую заявку? <tg-emoji emoji-id=\"5278747228939236605\">🤔</tg-emoji>",
                val onRestrict: String = "<b>Вы были ограничены в взаимодействии с нашим сервером ⛔</b>\nВы больше не сможете создавать и отправлять заявки.",
                val onAccept: String = "<b>Добро пожаловать на сервер! <tg-emoji emoji-id=\"5208541126583136130\">🎉</tg-emoji></b>",
                val onReject: String = "К сожалению, ваша заявка была отклонена. <tg-emoji emoji-id=\"5197279271361456513\">😞</tg-emoji>",
                val onCanceled: String = "Вы отменили свою заявку. Хотите создать новую? <tg-emoji emoji-id=\"5278747228939236605\">🤔</tg-emoji>",
                val onKick: String = "<b>К сожалению, вы были кикнуты с сервера. <tg-emoji emoji-id=\"5454350746407419714\">❌</tg-emoji></b>",
                val onReturn: String = "<b>С возвращением на сервер! <tg-emoji emoji-id=\"5462910521739063094\">👋</tg-emoji><b>",
            )
            data class RequestsBotForUserLangCreating (
                val needAgreeWithRules: String = "Для начала, необходимо ознакомиться и согласиться с <a href=\"https://teletype.in/@zixamc/rules-general\">правилами сервера</a>.\n<blockquote>Соглашаясь с настоящими правилами членства в составе игроков, вы также <i>соглашаетесь с остальными правилами</i> сервера, обязуясь соблюдать их. <b>Незнание правил не освобождает от ответственности!</b></blockquote>",
                val mustAgreeWithRules: String = "Для продолжения вы должны ознакомиться и согласиться с правилами сервера!",
                val needNickname: String = "Отлично! Придумайте себе никнейм для игры на сервере:",
                val wrongNickname: String = "Никнейм должен быть от 3 до 16 символов и содержать только символы a-z, A-Z, 0-9 и _",
                val takenNickname: String = "Такой никнейм уже занят!",
                val needRequestText: String = "Замечательно, настало время написать свою заявку. <tg-emoji emoji-id=\"5334882760735598374\">📝</tg-emoji>\n\nВ заявке вы должны описать себя, свой опыт игры в Minecraft и почему вы захотели стать игроком нашего сервера. Постарайтесь отвечать развёрнуто, что бы мы могли оценить вас.\nВы должны оформить свою заявку согласно <a href=\"https://teletype.in/@zixamc/rules-requests\">правилам создания и рассмотрения заявок</a>.",
                val confirmSendRequest: String = "Всё готово! Осталось только отправить заявку. <tg-emoji emoji-id=\"5406901223326495466\">🖥</tg-emoji>\nДля отправки заявки нажмите на кнопку <tg-emoji emoji-id=\"5197474438970363734\">⤵️</tg-emoji>",
                val youAreNowCreatingRequest: String = "В данный момент, вы уже пишете заявку, хотите начать сначала? <tg-emoji emoji-id=\"5278747228939236605\">🤔</tg-emoji>",
                val youHavePendingRequest: String = "Вы уже имеете заявку на рассмотрении! <tg-emoji emoji-id=\"538219493505737293\">⏱</tg-emoji>",
                val doYouWantToCancelRequest: String = "Вы хотите отменить свою заявку? <tg-emoji emoji-id=\"5445267414562389170\">🗑</tg-emoji>",
                val youAreNowPlayer: String = "Вы уже игрок сервера! <tg-emoji emoji-id=\"5429579672851596232\">😏</tg-emoji>",
            )
        }
    }
    data class RequestsBotForTarget (
        val chatId: Long = 0,
        val topicId: Int = 0,
        val lang: RequestsBotForTargetLang = RequestsBotForTargetLang(),
    ) {
        data class RequestsBotForTargetLang (
            val event: RequestsBotForTargetLangEvents = RequestsBotForTargetLangEvents(),
            val poll: RequestsBotForTargetLangPoll = RequestsBotForTargetLangPoll(),
        ) {
            data class RequestsBotForTargetLangEvents (
                val onSend: String = "<b>Внимание, новая заявка! <tg-emoji emoji-id=\"5220214598585568818\">🚨</tg-emoji></b>\n\nЧто бы задать вопрос заявителю, ответьте на заявку или на сообщение, отвечающее на заявку.\n\n<b>{mentionAll}</b>",
                val onCanceled: String = "<b>Заявка была отменена заявителем. <tg-emoji emoji-id=\"5210952531676504517\">❌</tg-emoji></b>",
                val onAccept: String = "<b>{nickname} теперь игрок сервера! <tg-emoji emoji-id=\"5217608395250485583\">🕺</tg-emoji></b>",
                val onReject: String = "<b>Заявка {nickname} была отклонена! <tg-emoji emoji-id=\"5210952531676504517\">❌</tg-emoji></b>",
                val onPromote: String = "Тип пользователя успешно изменён!",
                val onKick: String = "<b>{nickname} был кикнут с сервера. <tg-emoji emoji-id=\"5454350746407419714\">❌</tg-emoji></b>",
                val onRestrict: String = "<b>Пользователь {nickname} был ограничен в взаимодействии с сервером ⛔</b>",
                val onReturn: String = "<b>{nickname} вернулся на сервер! <tg-emoji emoji-id=\"5462910521739063094\">👋</tg-emoji></b>",
            )
            data class RequestsBotForTargetLangPoll (
                val question: String = "Добавлять {nickname} на сервер?",
                val answerTrue: String = "✅ Да",
                val answerNull: String = "💤 Не знаю",
                val answerFalse: String = "⛔ Нет",
            )
        }
    }
    data class RequestsBotForModerator (
        val chatId: Long = 0,
        val topicId: Int = 0,
        val lang: RequestsBotForModeratorLang = RequestsBotForModeratorLang(),
    ) {
        data class RequestsBotForModeratorLang (
            val button: RequestsBotForModeratorLangButtons = RequestsBotForModeratorLangButtons(),
            val event: RequestsBotForModeratorLangEvents = RequestsBotForModeratorLangEvents(),
        ) {
            data class RequestsBotForModeratorLangButtons (
                val approveSending: String = "Одобрить ✅",
                val denySending: String = "Отклонить ❌",
                val restrictSender: String = "Заблокировать заявителя ⛔",
                val closeRequestVote: String = "Подвести итоги голосования 🗳️",
            )
            data class RequestsBotForModeratorLangEvents (
                val onNew: String = "<b>Новая заявка от {nickname}!</b>\n\nВнимательно ознакомьтесь с заявкой и проверьте, соответствует ли она <a href=\"https://teletype.in/@zixamc/rules-general\">правилам сервера</a>. \nРазрешите отправку заявки игрокам только в том случае, если соблюдены все правила и критерии.\nПри необходимости, вы можете заблокировать этого заявителя (крайняя мера).",
                val onApprove: String = "<b>Заявка {nickname} была одобрена и уже отправлена в группу игроков!</b>",
                val onDeny: String = "<b>Заявка {nickname} была отклонена.</b>",
                val onCancel: String = "<b>{nickname} отменил(а) свою заявку.</b>",
                val onVoteClosed: String = "<b>Голосование за добавление {nickname} в состав игроков сервера было закрыто.</b>",
                val onUserRestricted: String = "<b>Заявитель {nickname} был заблокирован.</b>",
            )
        }
    }
    data class RequestsBotCommonLang (
        val command: RequestsBotTextCommandsDataClass = RequestsBotTextCommandsDataClass(),
    ) {
        data class RequestsBotTextCommandsDataClass (
            val acceptHelp: String = "Для того, что бы принять или отклонить заявку, ваше сообщение должно отвечать на заявку или на сообщение, отвечающее на заявку.",
            val rejectHelp: String = "Для того, что бы принять или отклонить заявку, ваше сообщение должно отвечать на заявку или на сообщение, отвечающее на заявку.",
            val promoteHelp: String = "Использование:\n/promote {user_id\nickname} {account_type/account_type_id}\n/promote {account_type/account_type_id} (при ответе на сообщение)\n\nПримеры:\n/promote PulpiLegend Admin\n/promote 0 (ответ на сообщение)",
            val kickHelp: String = "Использование:\n/kick {user_id\nickname}\n/kick (при ответе на сообщение)\n\nПримеры:\n/kick Kleverar\n/kick (ответ на сообщение)",
            val restrictHelp: String = "Использование:\n/restrict {user_id/nickname}\n/restrict (при ответе на сообщение)\n\nПримеры:\n/restrict Kleverar\n/restrict (ответ на сообщение)",
            val leaveHelp: String = "Использование:\n/leave {user_id/nickname}\n/leave (при ответе на сообщение)\n\nПримеры:\n/leave Kleverar\n/leave (ответ на сообщение)",
            val returnHelp: String = "Использование:\n/return {user_id/nickname}\n/return (при ответе на сообщение)\n\nПримеры:\n/return Kleverar\n/return (ответ на сообщение)",
            val permissionDenied: String = "Недостаточно прав для выполнения этой команды!",
        )
    }

    companion object: ConfigFile<Config>(
        file = File(FabricLoader.getInstance().configDir.toFile(), "ZixaMC-Requests.json"),
        model = Config::class.java,
        supplier = ::Config
    )
}