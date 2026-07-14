package date.oxi.wisepocket

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform