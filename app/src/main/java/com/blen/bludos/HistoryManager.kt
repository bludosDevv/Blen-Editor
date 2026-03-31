package com.blen.bludos

import java.util.Stack

class HistoryManager {

    private val undoStack = Stack<String>()
    private val redoStack = Stack<String>()

    fun pushState(stateJson: String) {
        // Prevent pushing duplicate consecutive states
        if (undoStack.isEmpty() || undoStack.peek() != stateJson) {
            undoStack.push(stateJson)
            redoStack.clear() // Clear redo on new action
        }
    }

    fun canUndo(): Boolean = undoStack.size > 1

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo(): String? {
        if (canUndo()) {
            val currentState = undoStack.pop()
            redoStack.push(currentState)
            return undoStack.peek()
        }
        return null
    }

    fun redo(): String? {
        if (canRedo()) {
            val nextState = redoStack.pop()
            undoStack.push(nextState)
            return nextState
        }
        return null
    }
}