package org.lodenstone.kurrent.spring

internal fun <T, K, V> Collection<T>.associateSafe(transform: (T) -> Pair<K, V>): Map<K, V> {
    return associate(transform).also { result ->
        check(result.size == this.size) { "" }
    }
}