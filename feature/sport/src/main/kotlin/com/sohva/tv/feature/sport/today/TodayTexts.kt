package com.sohva.tv.feature.sport.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.SportsProblem
import com.sohva.tv.core.model.sport.TodayFilter
import com.sohva.tv.ui.design.R

/** A provider failure in the spec's words (§7 error table), the quota named apart (rebuild). */
@Composable
internal fun problemText(problem: SportsProblem): String = stringResource(
    when (problem) {
        SportsProblem.KEY_MISSING -> R.string.error_api_sports_key_missing
        SportsProblem.HTTP, SportsProblem.UNAVAILABLE -> R.string.error_api_sports_unavailable
        SportsProblem.TOO_LARGE -> R.string.error_api_sports_response_too_large
        SportsProblem.SERVICE_ERROR -> R.string.error_api_sports_service_error
        SportsProblem.INVALID_DATA -> R.string.error_api_sports_invalid_data
        SportsProblem.QUOTA_EXHAUSTED -> R.string.error_api_sports_quota
    },
)

/** The line under the tabs while games are shown (SPORT-FR-54 rebuild): the screen-level title and the cause. */
@Composable
internal fun noticeText(problem: SportsProblem): String = stringResource(R.string.error_sports_partial) + " · " + problemText(problem)

/** SPORT-FR-54 per-filter empty texts. */
@Composable
internal fun emptyText(filter: TodayFilter): String = when (filter) {
    TodayFilter.All -> stringResource(R.string.empty_sports)
    TodayFilter.Watchable -> stringResource(R.string.empty_watchable)
    TodayFilter.Favourites -> stringResource(R.string.empty_favourites)
    is TodayFilter.OfSport -> stringResource(R.string.empty_sport, filterLabel(filter))
}

@Composable
internal fun filterLabel(filter: TodayFilter): String = stringResource(
    when (filter) {
        TodayFilter.All -> R.string.today_filter_all
        TodayFilter.Watchable -> R.string.today_filter_watchable
        TodayFilter.Favourites -> R.string.today_filter_favourites
        is TodayFilter.OfSport -> when (filter.sport) {
            SportType.FOOTBALL -> R.string.today_filter_football
            SportType.ICE_HOCKEY -> R.string.today_filter_hockey
            SportType.AUSTRALIAN_FOOTBALL -> R.string.today_filter_afl
            SportType.BASKETBALL -> R.string.today_filter_basketball
            SportType.BASEBALL -> R.string.today_filter_baseball
            SportType.HANDBALL -> R.string.today_filter_handball
            SportType.RUGBY -> R.string.today_filter_rugby
            SportType.VOLLEYBALL -> R.string.today_filter_volleyball
            SportType.AMERICAN_FOOTBALL -> R.string.today_filter_nfl
            SportType.MMA -> R.string.today_filter_mma
            SportType.FORMULA_1 -> R.string.today_filter_formula1
            SportType.NBA -> R.string.today_filter_nba
        }
    },
)
