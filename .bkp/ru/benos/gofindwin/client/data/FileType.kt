package ru.benos.gofindwin.client.data

import ru.benos.gofindwin.GoFindWinConst

enum class FileType(val dir: String) {
    CLIENT(GoFindWinConst.Client.CONFIG_FILE_CLIENT),
    COMMON(GoFindWinConst.CONFIG_FILE_COMMON),

    CACHE_EFFECTS("${GoFindWinConst.Client.DIRECTORY_CACHE}/${GoFindWinConst.Client.EFFECTS_JSON}"),
    CACHE_SCROLLS("${GoFindWinConst.Client.DIRECTORY_CACHE}/${GoFindWinConst.Client.SCROLLS_JSON}"),

    PROFILES_EFFECTS("${GoFindWinConst.Client.DIRECTORY_PROFILES}/${GoFindWinConst.Client.EFFECTS_JSON}"),
    PROFILES_SCROLLS("${GoFindWinConst.Client.DIRECTORY_PROFILES}/${GoFindWinConst.Client.SCROLLS_JSON}")
}