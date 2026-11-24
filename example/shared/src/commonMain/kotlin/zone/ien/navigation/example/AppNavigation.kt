package zone.ien.navigation.example

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
data object HomeRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data class DetailRoute(val id: Int, val preview: Boolean = false) : NavKey

internal const val tabBarHiddenDetailId = 4

// Navigation 3의 KMP 백스택 저장 설정. 네이티브 내비게이션을 쓰지 않는 앱에도 필요하다.
val navigationConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(HomeRoute::class)
            subclass(SettingsRoute::class)
            subclass(DetailRoute::class)
        }
    }
}
