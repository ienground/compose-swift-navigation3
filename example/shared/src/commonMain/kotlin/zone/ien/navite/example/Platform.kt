package zone.ien.navite.example

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform