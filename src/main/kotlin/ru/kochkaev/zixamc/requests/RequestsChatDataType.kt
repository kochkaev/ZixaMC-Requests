package ru.kochkaev.zixamc.requests

import com.google.gson.reflect.TypeToken
import ru.kochkaev.zixamc.api.sql.SQLUser
import ru.kochkaev.zixamc.api.sql.chatdata.ChatDataType
import ru.kochkaev.zixamc.api.sql.data.AccountType
import java.util.ArrayList

object RequestsChatDataType: ChatDataType<ArrayList<RequestData>>(
    model = object: TypeToken<ArrayList<RequestData>>(){}.type,
    serializedName = "requests",
) {
    fun addRequest(requestData: RequestData, user: SQLUser) {
        if (user.accountType == AccountType.UNKNOWN) user.accountType = AccountType.REQUESTER
        val requests = user.data.getCasted(RequestsChatDataType) ?: arrayListOf()
        requests.add(requestData)
        user.data.set(RequestsChatDataType, requests)
    }
    fun editRequest(requestData: RequestData, user: SQLUser) {
        val requests = user.data.getCasted(RequestsChatDataType) ?: return
        requests.removeIf { it.requestId == requestData.requestId }
        requests.add(requestData)
        user.data.set(RequestsChatDataType, requests)
    }
}