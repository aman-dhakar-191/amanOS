package com.amanOS.core

import android.os.Bundle

sealed class AgentResult {

    data class Success(val data: Bundle = Bundle()) : AgentResult()

    data class Error(
        val code: Int = AgentContract.ResultCode.ERROR,
        val message: String
    ) : AgentResult()

    object NotFound : AgentResult()

    fun toBundle(): Bundle = Bundle().apply {
        when (this@AgentResult) {
            is Success -> {
                putInt(AgentContract.Extras.RESULT_CODE, AgentContract.ResultCode.SUCCESS)
                putAll(data)
            }
            is Error -> {
                putInt(AgentContract.Extras.RESULT_CODE, code)
                putString(AgentContract.Extras.RESULT_MESSAGE, message)
            }
            is NotFound -> {
                putInt(AgentContract.Extras.RESULT_CODE, AgentContract.ResultCode.NOT_FOUND)
            }
        }
    }

    companion object {
        fun fromBundle(bundle: Bundle): AgentResult {
            return when (bundle.getInt(AgentContract.Extras.RESULT_CODE)) {
                AgentContract.ResultCode.SUCCESS -> Success(bundle)
                AgentContract.ResultCode.NOT_FOUND -> NotFound
                else -> Error(
                    code = bundle.getInt(AgentContract.Extras.RESULT_CODE),
                    message = bundle.getString(AgentContract.Extras.RESULT_MESSAGE, "Unknown error")
                )
            }
        }
    }
}
