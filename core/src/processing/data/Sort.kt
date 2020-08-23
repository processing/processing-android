package processing.data

/**
 * @author Aditya Rana
 * Internal sorter used by several data classes.
 * Advanced users only, not official API.
 */
abstract class Sort : Runnable {
    override fun run() {
        val c = size()
        if (c > 1) {
            sort(0, c - 1)
        }
    }

    protected fun sort(i: Int, j: Int) {
        val pivotIndex = (i + j) / 2
        swap(pivotIndex, j)
        val k = partition(i - 1, j)
        swap(k, j)
        if (k - i > 1) sort(i, k - 1)
        if (j - k > 1) sort(k + 1, j)
    }

    protected fun partition(left: Int, right: Int): Int {
        var left = left
        var right = right
        val pivot = right
        do {
            while (compare(++left, pivot) < 0) {
            }
            while (right != 0 && compare(--right, pivot) > 0) {
            }
            swap(left, right)
        } while (left < right)
        swap(left, right)
        return left
    }

    abstract fun size(): Int
    abstract fun compare(a: Int, b: Int): Int
    abstract fun swap(a: Int, b: Int)
}