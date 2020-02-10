package ooo.emessi.messenger.data.model.bz_model.account

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bz_account")
data class BZAccount(
    @ColumnInfo (name = "user_jid")
    val userJid: String,
    @ColumnInfo (name = "password")
    val password: String,
    @ColumnInfo (name = "host")
    val host: String,
    @PrimaryKey
    @ColumnInfo (name = "id")
    val id: Int = 0
)