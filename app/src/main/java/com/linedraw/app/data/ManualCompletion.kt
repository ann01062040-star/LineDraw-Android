package com.linedraw.app.data

import org.json.JSONObject

fun Record?.canMarkCompletedManually() = this == null || status in setOf("SUBMITTED", "REVIEW")

/** Stored alongside existing metadata so undo restores prior evidence, not a retryable blank. */
internal object ManualCompletion {
    fun key(profile: String, activityKey: String) = "manualUndo:" + digest("${profile.length}:$profile:$activityKey")
    fun encode(profile: String, activityKey: String, previous: Record?): String = JSONObject()
        .put("version",1).put("profile",profile).put("activityKey",activityKey)
        .put("previous",previous?.let { r -> JSONObject()
            .put("product",r.product).put("store",r.store).put("status",r.status)
            .put("result",r.result).put("evidence",r.evidence).put("updatedAt",r.updatedAt)
        } ?: JSONObject.NULL).toString()

    fun decode(value: String, profile: String, activityKey: String): Record? {
        val json=JSONObject(value)
        require(json.getInt("version")==1 && json.getString("profile")==profile && json.getString("activityKey")==activityKey)
        require(json.has("previous")) { "缺少原始紀錄，已保留手動完成狀態" }
        if (json.isNull("previous")) return null
        val old=json.getJSONObject("previous")
        val status=old.getString("status")
        require(status in setOf("SUBMITTED","REVIEW")) { "原始紀錄格式不符，已保留手動完成狀態" }
        return Record(profile,activityKey,old.getString("product"),old.getString("store"),status,
            old.getString("result"),old.getString("evidence"),old.getLong("updatedAt"))
    }
}
