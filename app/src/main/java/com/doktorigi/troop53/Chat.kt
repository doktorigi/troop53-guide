package com.doktorigi.troop53

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

data class Member(val uid: String, val name: String, val role: String, val approved: Boolean, val leader: Boolean)
data class Group(val id: String, val name: String, val members: List<String>, val createdBy: String, val lastAt: String)
data class Msg(val id: String, val uid: String, val name: String, val text: String, val at: String)

class ChatException(message: String, val code: Int = 0) : IOException(message)

fun millis(ts: String) = runCatching { Instant.parse(ts).toEpochMilli() }.getOrDefault(0L)

/**
 * Troop chat on Firebase Auth + Firestore, over plain REST so the app keeps zero dependencies.
 * Every permission check lives in firestore.rules; this client is untrusted.
 */
class Chat(context: Context) {
    private val prefs = context.getSharedPreferences("chat", Context.MODE_PRIVATE)
    val uid: String? get() = prefs.getString("uid", null)
    val email: String? get() = prefs.getString("email", null)

    // ---------- account ----------

    fun signIn(email: String, password: String) = auth("accounts:signInWithPassword", email, password)

    fun signUp(email: String, password: String, name: String, role: String) {
        auth("accounts:signUp", email, password)
        createProfile(name, role)
    }

    fun createProfile(name: String, role: String) =
        commit(create("users/$uid", fields("name" to name, "role" to role, "approved" to false, "leader" to false)))

    fun resetPassword(email: String) {
        call("POST", "$AUTH/accounts:sendOobCode?key=$API_KEY", JSONObject().put("requestType", "PASSWORD_RESET").put("email", email).toString())
    }

    fun signOut() = prefs.edit().clear().apply()

    /** Firebase only deletes accounts after a fresh sign-in, so this re-checks the password first. */
    fun deleteAccount(password: String) {
        signIn(email!!, password)
        delete("users/$uid")
        call("POST", "$AUTH/accounts:delete?key=$API_KEY", JSONObject().put("idToken", token()).toString())
        signOut()
    }

    private fun auth(endpoint: String, email: String, password: String) {
        val r = JSONObject(call("POST", "$AUTH/$endpoint?key=$API_KEY",
            JSONObject().put("email", email).put("password", password).put("returnSecureToken", true).toString()))
        prefs.edit().putString("uid", r.getString("localId")).putString("email", email).apply()
        saveToken(r.getString("idToken"), r.getString("refreshToken"), r.getString("expiresIn"))
    }

    private fun saveToken(id: String, refresh: String, expiresIn: String) = prefs.edit()
        .putString("id", id).putString("refresh", refresh)
        .putLong("exp", System.currentTimeMillis() + expiresIn.toLong() * 1000).apply()

    private fun token(): String = synchronized(LOCK) {
        if (System.currentTimeMillis() < prefs.getLong("exp", 0) - 60_000) return prefs.getString("id", null)!!
        val refresh = prefs.getString("refresh", null) ?: throw ChatException("Please sign in.")
        val r = try {
            JSONObject(call("POST", "https://securetoken.googleapis.com/v1/token?key=$API_KEY",
                "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8"), form = true))
        } catch (e: ChatException) {
            if (e.code in 400..499) signOut() // account deleted or disabled
            throw e
        }
        saveToken(r.getString("id_token"), r.getString("refresh_token"), r.getString("expires_in"))
        r.getString("id_token")
    }

    // ---------- people ----------

    fun me(): Member? = uid?.let { get("users/$it") }?.let(::member)

    // ponytail: one page of 300 users is plenty for a troop
    fun members(): List<Member> = docs(JSONObject(call("GET", "$DOCS/users?pageSize=300", token = token())).optJSONArray("documents")).map(::member)

    fun setUser(uid: String, approved: Boolean, leader: Boolean) = commit(
        update("users/$uid", fields("approved" to approved, "leader" to leader)))

    fun removeUser(uid: String) = delete("users/$uid")

    // ---------- groups ----------

    fun group(id: String): Group? = get("groups/$id")?.let(::group)

    /** Leaders see every group (Youth Protection oversight); everyone else sees groups they belong to. */
    fun groups(me: Member): List<Group> {
        val q = from("groups")
        if (!me.leader) q.put("where", filter("members", "ARRAY_CONTAINS", enc(me.uid)))
        return query("", q).map(::group).sortedByDescending { millis(it.lastAt) }
    }

    fun createGroup(name: String, members: List<String>) =
        commit(create("groups/${newId()}", fields("name" to name, "members" to members, "createdBy" to uid), "lastAt"))

    fun editGroup(id: String, name: String, members: List<String>) = commit(update("groups/$id", fields("name" to name, "members" to members)))

    // ponytail: messages of a deleted group are orphaned (unreadable by rules); add cleanup if storage ever matters
    fun deleteGroup(id: String) = delete("groups/$id")

    // ---------- messages ----------

    /** The newest [latest] messages, or everything after [after] (a server timestamp), oldest first. */
    fun messages(groupId: String, after: String?, latest: Int = 100): List<Msg> {
        val q = from("messages")
        if (after != null) q.put("where", filter("at", "GREATER_THAN", JSONObject().put("timestampValue", after)))
        q.put("orderBy", JSONArray().put(JSONObject().put("field", JSONObject().put("fieldPath", "at"))
            .put("direction", if (after == null) "DESCENDING" else "ASCENDING")))
        if (after == null) q.put("limit", latest)
        val msgs = query("groups/$groupId", q).map { Msg(it.id, it.s("uid"), it.s("name"), it.s("text"), it.s("at")) }
        return if (after == null) msgs.reversed() else msgs
    }

    fun send(groupId: String, name: String, text: String) = commit(
        create("groups/$groupId/messages/${newId()}", fields("uid" to uid, "name" to name, "text" to text), "at"),
        JSONObject().put("transform", JSONObject().put("document", NAME + "groups/$groupId").put("fieldTransforms", serverTime("lastAt"))))

    fun deleteMessage(groupId: String, id: String) = delete("groups/$groupId/messages/$id")

    // ---------- Firestore REST plumbing ----------

    private class Doc(j: JSONObject) {
        val id: String = j.getString("name").substringAfterLast('/')
        private val f = j.optJSONObject("fields") ?: JSONObject()
        fun s(k: String): String = f.optJSONObject(k)?.let { it.optString("stringValue", null) ?: it.optString("timestampValue", "") } ?: ""
        fun b(k: String) = f.optJSONObject(k)?.optBoolean("booleanValue") ?: false
        fun list(k: String): List<String> {
            val a = f.optJSONObject(k)?.optJSONObject("arrayValue")?.optJSONArray("values") ?: return emptyList()
            return List(a.length()) { a.getJSONObject(it).optString("stringValue") }
        }
    }

    private fun member(d: Doc) = Member(d.id, d.s("name"), d.s("role"), d.b("approved"), d.b("leader"))
    private fun group(d: Doc) = Group(d.id, d.s("name"), d.list("members"), d.s("createdBy"), d.s("lastAt"))
    private fun docs(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { Doc(a.getJSONObject(it)) }

    private fun get(path: String): Doc? = try {
        Doc(JSONObject(call("GET", "$DOCS/$path", token = token())))
    } catch (e: ChatException) {
        if (e.code == 404) null else throw e
    }

    private fun delete(path: String) { call("DELETE", "$DOCS/$path", token = token()) }

    private fun query(parent: String, q: JSONObject): List<Doc> {
        val a = JSONArray(call("POST", "$DOCS${if (parent.isEmpty()) "" else "/$parent"}:runQuery", JSONObject().put("structuredQuery", q).toString(), token()))
        return (0 until a.length()).mapNotNull { a.getJSONObject(it).optJSONObject("document")?.let(::Doc) }
    }

    private fun commit(vararg writes: JSONObject) {
        call("POST", "$DOCS:commit", JSONObject().put("writes", JSONArray(writes.toList())).toString(), token())
    }

    private fun doc(path: String, f: JSONObject) = JSONObject().put("name", NAME + path).put("fields", f)

    private fun create(path: String, f: JSONObject, timeField: String? = null) = JSONObject()
        .put("update", doc(path, f)).put("currentDocument", JSONObject().put("exists", false))
        .apply { if (timeField != null) put("updateTransforms", serverTime(timeField)) }

    private fun update(path: String, f: JSONObject) = JSONObject()
        .put("update", doc(path, f)).put("currentDocument", JSONObject().put("exists", true))
        .put("updateMask", JSONObject().put("fieldPaths", JSONArray(f.keys().asSequence().toList())))

    private fun serverTime(field: String) = JSONArray().put(JSONObject().put("fieldPath", field).put("setToServerValue", "REQUEST_TIME"))
    private fun from(collection: String) = JSONObject().put("from", JSONArray().put(JSONObject().put("collectionId", collection)))
    private fun filter(field: String, op: String, value: JSONObject) = JSONObject().put("fieldFilter",
        JSONObject().put("field", JSONObject().put("fieldPath", field)).put("op", op).put("value", value))
    private fun newId() = UUID.randomUUID().toString().replace("-", "")

    private fun enc(v: Any?): JSONObject = when (v) {
        is String -> JSONObject().put("stringValue", v)
        is Boolean -> JSONObject().put("booleanValue", v)
        is List<*> -> JSONObject().put("arrayValue", JSONObject().put("values", JSONArray(v.map(::enc))))
        else -> throw IllegalArgumentException("Unsupported value $v")
    }

    private fun fields(vararg p: Pair<String, Any?>) = JSONObject().apply { p.forEach { (k, v) -> put(k, enc(v)) } }

    companion object {
        const val PROJECT = "troop53-guide"
        private const val API_KEY = BuildConfig.FIREBASE_KEY
        private const val AUTH = "https://identitytoolkit.googleapis.com/v1"
        private const val NAME = "projects/$PROJECT/databases/(default)/documents/"
        private const val DOCS = "https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents"
        private val LOCK = Any()

        private fun call(method: String, url: String, body: String? = null, token: String? = null, form: Boolean = false): String {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = method
                c.connectTimeout = 15_000
                c.readTimeout = 20_000
                token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", if (form) "application/x-www-form-urlencoded" else "application/json; charset=utf-8")
                    c.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = c.responseCode
                val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code >= 400) throw ChatException(friendly(text), code)
                return text
            } catch (e: ChatException) {
                throw e
            } catch (e: IOException) {
                throw ChatException("No connection. Chat needs the internet.")
            } finally {
                c.disconnect()
            }
        }

        private fun friendly(body: String): String {
            val msg = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrDefault("")
            fun any(vararg codes: String) = codes.any { msg.startsWith(it) }
            return when {
                any("EMAIL_EXISTS") -> "That email already has an account. Sign in instead."
                any("INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND") -> "Wrong email or password."
                any("WEAK_PASSWORD") -> "Use a password with at least 6 characters."
                any("INVALID_EMAIL", "MISSING_EMAIL") -> "Enter a valid email address."
                any("MISSING_PASSWORD") -> "Enter your password."
                any("TOO_MANY_ATTEMPTS") -> "Too many tries. Wait a few minutes."
                any("TOKEN_EXPIRED", "CREDENTIAL_TOO_OLD", "USER_NOT_FOUND", "USER_DISABLED", "INVALID_REFRESH_TOKEN") -> "Please sign in again."
                "permission" in msg.lowercase() -> "Not allowed. Ask a troop leader."
                else -> msg.ifBlank { "Something went wrong. Try again." }
            }
        }
    }
}
