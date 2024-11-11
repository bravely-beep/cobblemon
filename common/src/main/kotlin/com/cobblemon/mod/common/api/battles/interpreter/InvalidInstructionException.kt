package com.cobblemon.mod.common.api.battles.interpreter

import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction

/**
 * An exception thrown when properties from a [BattleMessage] cannot be interpreted for an [InterpreterInstruction].
 *
 * @author Segfault Guy
 * @since November 10th, 2024
 */
class InvalidInstructionException(battleMessage: BattleMessage) : Exception("Failed to interpret ${battleMessage.rawMessage}")