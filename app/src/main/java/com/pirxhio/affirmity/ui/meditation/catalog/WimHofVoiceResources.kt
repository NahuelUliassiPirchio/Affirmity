package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.meditation.SessionDirectorFactory
import com.pirxhio.affirmity.meditation.wimhof.voice.WhmVoiceDirector

/** Wim Hof voice clip id -> `@RawRes`. Raw resource name == audio id; keys mirror `WhmClips.voiceMillis`. */
internal val WIM_HOF_VOICE_RESOURCES: Map<String, Int> = mapOf(
    "whm_vo_cierre_1" to R.raw.whm_vo_cierre_1,
    "whm_vo_cierre_2" to R.raw.whm_vo_cierre_2,
    "whm_vo_cierre_3" to R.raw.whm_vo_cierre_3,
    "whm_vo_cierre_4" to R.raw.whm_vo_cierre_4,
    "whm_vo_cuenta_3" to R.raw.whm_vo_cuenta_3,
    "whm_vo_cuenta_5" to R.raw.whm_vo_cuenta_5,
    "whm_vo_cuenta_ultima" to R.raw.whm_vo_cuenta_ultima,
    "whm_vo_entre_1" to R.raw.whm_vo_entre_1,
    "whm_vo_entre_2" to R.raw.whm_vo_entre_2,
    "whm_vo_entre_3" to R.raw.whm_vo_entre_3,
    "whm_vo_intro_1" to R.raw.whm_vo_intro_1,
    "whm_vo_intro_2" to R.raw.whm_vo_intro_2,
    "whm_vo_intro_3" to R.raw.whm_vo_intro_3,
    "whm_vo_rec_cuenta" to R.raw.whm_vo_rec_cuenta,
    "whm_vo_rec_inicio" to R.raw.whm_vo_rec_inicio,
    "whm_vo_resp_01" to R.raw.whm_vo_resp_01,
    "whm_vo_resp_02" to R.raw.whm_vo_resp_02,
    "whm_vo_resp_03" to R.raw.whm_vo_resp_03,
    "whm_vo_resp_04" to R.raw.whm_vo_resp_04,
    "whm_vo_resp_05" to R.raw.whm_vo_resp_05,
    "whm_vo_resp_06" to R.raw.whm_vo_resp_06,
    "whm_vo_resp_07" to R.raw.whm_vo_resp_07,
    "whm_vo_resp_08" to R.raw.whm_vo_resp_08,
    "whm_vo_resp_09" to R.raw.whm_vo_resp_09,
    "whm_vo_resp_10" to R.raw.whm_vo_resp_10,
    "whm_vo_resp_11" to R.raw.whm_vo_resp_11,
    "whm_vo_resp_12" to R.raw.whm_vo_resp_12,
    "whm_vo_ret_01" to R.raw.whm_vo_ret_01,
    "whm_vo_ret_02" to R.raw.whm_vo_ret_02,
    "whm_vo_ret_03" to R.raw.whm_vo_ret_03,
    "whm_vo_ret_04" to R.raw.whm_vo_ret_04,
    "whm_vo_ret_05" to R.raw.whm_vo_ret_05,
    "whm_vo_ret_06" to R.raw.whm_vo_ret_06,
    "whm_vo_ret_07" to R.raw.whm_vo_ret_07,
    "whm_vo_ret_08" to R.raw.whm_vo_ret_08,
    "whm_vo_ret_09" to R.raw.whm_vo_ret_09,
    "whm_vo_ret_10" to R.raw.whm_vo_ret_10,
    "whm_vo_ret_11" to R.raw.whm_vo_ret_11,
    "whm_vo_ret_inicio" to R.raw.whm_vo_ret_inicio,
    "whm_vo_ronda_1" to R.raw.whm_vo_ronda_1,
    "whm_vo_ronda_2" to R.raw.whm_vo_ronda_2,
    "whm_vo_ronda_3" to R.raw.whm_vo_ronda_3,
    "whm_vo_ronda_4" to R.raw.whm_vo_ronda_4,
    "whm_vo_ronda_final" to R.raw.whm_vo_ronda_final,
    "whm_vo_tiempo_030" to R.raw.whm_vo_tiempo_030,
    "whm_vo_tiempo_100" to R.raw.whm_vo_tiempo_100,
    "whm_vo_tiempo_130" to R.raw.whm_vo_tiempo_130,
    "whm_vo_tiempo_200" to R.raw.whm_vo_tiempo_200,
    "whm_vo_tiempo_230" to R.raw.whm_vo_tiempo_230,
    "whm_vo_tiempo_300" to R.raw.whm_vo_tiempo_300,
    "whm_vo_tiempo_330" to R.raw.whm_vo_tiempo_330,
    "whm_vo_tiempo_400" to R.raw.whm_vo_tiempo_400,
)

/** Non-capturing, so the presentation stays structurally equal across catalog builds. */
internal val WIM_HOF_DIRECTOR_FACTORY: SessionDirectorFactory = { sink, state -> WhmVoiceDirector(sink, state) }
