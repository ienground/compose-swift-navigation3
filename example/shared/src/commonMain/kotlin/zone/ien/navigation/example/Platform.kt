package zone.ien.navigation.example

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform