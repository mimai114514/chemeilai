package io.github.mimai114514.chemeilai.ui.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.mimai114514.chemeilai.ui.city.CityPickerScreen
import io.github.mimai114514.chemeilai.ui.favorites.FavoritesScreen
import io.github.mimai114514.chemeilai.ui.line.LineDetailScreen
import io.github.mimai114514.chemeilai.ui.nearby.NearbyScreen
import io.github.mimai114514.chemeilai.ui.realtime.RealtimeScreen
import io.github.mimai114514.chemeilai.ui.search.SearchScreen
import io.github.mimai114514.chemeilai.ui.station.StationDetailScreen

private object Routes {
    const val NEARBY = "nearby"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val CITY = "city"
    const val STATION = "station/{sId}?name={name}"
    const val REALTIME = "realtime/{sId}/{lineNo}/{direction}?name={name}"
    const val LINE = "line/{lineName}?cityId={cityId}&direction={direction}&lineId={lineId}"

    fun station(sId: String, name: String): String =
        "station/${Uri.encode(sId)}?name=${Uri.encode(name)}"

    fun realtime(sId: String, lineNo: String, direction: Int, name: String): String =
        "realtime/${Uri.encode(sId)}/${Uri.encode(lineNo)}/$direction?name=${Uri.encode(name)}"

    fun line(lineName: String, cityId: String?, direction: Int?, lineId: String?): String = buildString {
        append("line/").append(Uri.encode(lineName))
        append("?cityId=").append(Uri.encode(cityId.orEmpty()))
        append("&direction=").append(direction ?: -1)
        append("&lineId=").append(Uri.encode(lineId.orEmpty()))
    }
}

@Composable
fun CheMeiLaiNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = TopDestination.entries.any { it.route == currentRoute }

    fun goTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (isTopLevel) {
                CheMeiLaiBottomBar(currentRoute = currentRoute) { destination ->
                    goTopLevel(destination.route)
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.NEARBY,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(Routes.NEARBY) {
                NearbyScreen(
                    onStationClick = { stop ->
                        navController.navigate(Routes.station(stop.sId, stop.name))
                    },
                    onLineClick = { stop, line ->
                        navController.navigate(
                            Routes.realtime(stop.sId, line.lineNo, line.direction, line.displayName),
                        )
                    },
                    onPickCity = { navController.navigate(Routes.CITY) },
                    onSearchClick = { navController.navigate(Routes.SEARCH) },
                )
            }

            composable(
                route = Routes.SEARCH,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            ) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onStationClick = { station ->
                        navController.navigate(Routes.station(station.sId, station.name))
                    },
                    onLineClick = { line, cityId ->
                        navController.navigate(
                            Routes.line(
                                lineName = line.displayName,
                                cityId = cityId,
                                direction = line.direction,
                                lineId = line.lineId,
                            ),
                        )
                    },
                    onPickCity = { navController.navigate(Routes.CITY) },
                )
            }

            composable(Routes.FAVORITES) {
                FavoritesScreen(
                    onStationClick = { favorite ->
                        val stationId = favorite.stationId
                        if (stationId != null) {
                            navController.navigate(
                                Routes.station(stationId, favorite.stationName.orEmpty()),
                            )
                        }
                    },
                    onLineClick = { status ->
                        val favorite = status.favorite
                        if (status.ready && status.stationId != null && status.lineNo != null && status.direction != null) {
                            navController.navigate(
                                Routes.realtime(
                                    status.stationId,
                                    status.lineNo,
                                    status.direction,
                                    favorite.lineName.orEmpty(),
                                ),
                            )
                        } else {
                            navController.navigate(
                                Routes.line(
                                    lineName = favorite.lineName.orEmpty(),
                                    cityId = favorite.cityId,
                                    direction = favorite.direction,
                                    lineId = favorite.lineId,
                                ),
                            )
                        }
                    },
                )
            }

            composable(Routes.CITY) {
                CityPickerScreen(
                    onBack = { navController.popBackStack() },
                    onSelected = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.STATION,
                arguments = listOf(
                    navArgument("sId") { type = NavType.StringType },
                    navArgument("name") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                StationDetailScreen(
                    stationId = entry.arguments?.getString("sId").orEmpty(),
                    stationName = entry.arguments?.getString("name").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onLineClick = { resolvedId, line ->
                        navController.navigate(
                            Routes.realtime(resolvedId, line.lineNo, line.direction, line.displayName),
                        )
                    },
                )
            }

            composable(
                route = Routes.REALTIME,
                arguments = listOf(
                    navArgument("sId") { type = NavType.StringType },
                    navArgument("lineNo") { type = NavType.StringType },
                    navArgument("direction") { type = NavType.IntType },
                    navArgument("name") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                RealtimeScreen(
                    stationId = entry.arguments?.getString("sId").orEmpty(),
                    lineNo = entry.arguments?.getString("lineNo").orEmpty(),
                    direction = entry.arguments?.getInt("direction") ?: 0,
                    lineName = entry.arguments?.getString("name").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.LINE,
                arguments = listOf(
                    navArgument("lineName") { type = NavType.StringType },
                    navArgument("cityId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("direction") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                    navArgument("lineId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                LineDetailScreen(
                    cityId = entry.arguments?.getString("cityId")?.takeIf { it.isNotBlank() },
                    lineName = entry.arguments?.getString("lineName").orEmpty(),
                    direction = entry.arguments?.getInt("direction")?.takeIf { it >= 0 },
                    lineId = entry.arguments?.getString("lineId")?.takeIf { it.isNotBlank() },
                    onBack = { navController.popBackStack() },
                    onStationClick = { station ->
                        val sId = station.sId
                        if (sId != null) {
                            navController.navigate(Routes.station(sId, station.name))
                        }
                    },
                )
            }
        }
    }
}
