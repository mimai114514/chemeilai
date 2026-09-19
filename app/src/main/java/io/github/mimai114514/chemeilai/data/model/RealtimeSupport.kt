package io.github.mimai114514.chemeilai.data.model

/**
 * 实时数据可用性（用于城市选择页提示）。
 *
 * [CHELAILE_CHANNELS] 来自车来了 H5 前端 bundle 内硬编码的渠道配置表（`scripts-*.js` 里的
 * `src:"wechat_xxx"` 与对应 `cityId`），共 70 个渠道 / 54 个城市；车来了 H5 只对这些城市
 * 返回车辆实时数据，其它城市只返回线路、站点、首末班等静态信息。上游更新后需要重新同步。
 */
object RealtimeSupport {

    val CHELAILE_CHANNELS: Set<String> = setOf(
        "003", "008", "019", "046", "066", "070", "074", "076", "100", "101",
        "11023", "11027", "11049", "113", "140", "155", "157", "158", "160", "173",
        "193", "241", "251", "254", "277", "278", "300", "301", "316", "320",
        "342", "365", "372", "391", "392", "395", "440", "455", "505", "521",
        "534", "551", "900", "909", "910", "911", "914", "916", "928", "929",
        "930", "945", "975", "976",
    )

    /** 通卡（yourbus.tongda.cc）数据源覆盖的城市。 */
    val TONGDA: Set<String> = setOf("169")

    fun hasRealtime(cityId: String): Boolean =
        cityId in TONGDA || cityId in CHELAILE_CHANNELS
}
