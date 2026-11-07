package com.example.glasslauncher.data

/** Where a dragged item came from. */
sealed interface DragSource {
    data object Drawer : DragSource
    data object Home : DragSource
    data object Dock : DragSource
    data class Folder(val id: String) : DragSource
}

/** Where it was dropped. Indexes are in the list *without* the dragged item. */
sealed interface DropTarget {
    data object None : DropTarget
    data class Home(val index: Int) : DropTarget
    data class Merge(val targetId: String) : DropTarget
    data class Dock(val index: Int) : DropTarget
    data object Remove : DropTarget
    data object Uninstall : DropTarget
}
