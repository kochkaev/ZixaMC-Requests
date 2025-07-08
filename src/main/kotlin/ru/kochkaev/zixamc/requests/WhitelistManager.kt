package ru.kochkaev.zixamc.requests

import com.mojang.authlib.GameProfile
import net.minecraft.server.Whitelist
import net.minecraft.server.WhitelistEntry
import net.minecraft.text.Text
import net.minecraft.util.Uuids
import ru.kochkaev.zixamc.api.ZixaMC

object WhitelistManager {

    fun add(nickname:String): Boolean {
        val profiles = getProfileFromNickname(nickname)
        val server = ZixaMC.server?:return false
        val whitelist: Whitelist = server.playerManager.whitelist
        var i = 0
        for (gameProfile in profiles) {
            if (!whitelist.isAllowed(gameProfile)) {
                val whitelistEntry = WhitelistEntry(gameProfile)
                whitelist.add(whitelistEntry)
                server.commandSource.sendFeedback( {
                    Text.translatable(
                        "commands.whitelist.add.success",
                        *arrayOf<Any>(Text.literal(gameProfile.name))
                    )
                }, true)
                ++i
            }
        }
        return i != 0
    }

    fun remove(nickname:String): Boolean {
        val profiles = getProfileFromNickname(nickname)
        val server = ZixaMC.server?:return false
        val whitelist: Whitelist = server.playerManager.whitelist
        var i = 0
        for (gameProfile in profiles) {
            if (whitelist.isAllowed(gameProfile)) {
                val whitelistEntry = WhitelistEntry(gameProfile)
                whitelist.remove(whitelistEntry)
                server.commandSource.sendFeedback( {
                    Text.translatable(
                        "commands.whitelist.remove.success",
                        *arrayOf<Any>(Text.literal(gameProfile.name))
                    )
                }, true)
                ++i
            }
        }
        server.kickNonWhitelistedPlayers(server.commandSource)
        return i != 0
    }

    /**
     * @author NikitaCartes
     */
    private fun getProfileFromNickname(name: String?): Collection<GameProfile> {
        return listOf(GameProfile(Uuids.getOfflinePlayerUuid(name), name))
    }
}