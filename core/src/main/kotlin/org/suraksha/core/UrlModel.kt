package org.suraksha.core

import kotlinx.serialization.json.*

/** ExtraTrees export scored using primitive arrays; no ML runtime or network. */
class UrlModel(json: String) {
    private data class Tree(val left: IntArray, val right: IntArray, val features: IntArray,
                            val thresholds: DoubleArray, val values: DoubleArray)
    private val root = Json.parseToJsonElement(json).jsonObject
    val suspiciousThreshold = root.getValue("suspicious_threshold").jsonPrimitive.double
    val dangerousThreshold = root.getValue("dangerous_threshold").jsonPrimitive.double
    private val trees: List<Tree>
    init {
        require(root.getValue("schema_version").jsonPrimitive.int == 1)
        require(root.getValue("feature_names").jsonArray.map { it.jsonPrimitive.content } == UrlFeatures.NAMES)
        require(root.getValue("dtype").jsonPrimitive.content == "float32")
        trees = root.getValue("trees").jsonArray.map { item ->
            val obj = item.jsonObject
            fun ints(key: String) = obj.getValue(key).jsonArray.map { it.jsonPrimitive.int }.toIntArray()
            fun doubles(key: String) = obj.getValue(key).jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
            Tree(ints("left"), ints("right"), ints("feature"), doubles("threshold"), doubles("value"))
                .also { tree ->
                    require(tree.left.isNotEmpty() && tree.left.size == tree.right.size &&
                        tree.left.size == tree.features.size && tree.left.size == tree.thresholds.size &&
                        tree.left.size == tree.values.size)
                    tree.left.indices.forEach { i ->
                        require(tree.values[i].isFinite() && tree.values[i] in 0.0..1.0)
                        require(tree.features[i] == -2 || tree.features[i] in UrlFeatures.NAMES.indices)
                        if (tree.features[i] >= 0) require(tree.left[i] in tree.left.indices && tree.right[i] in tree.left.indices)
                    }
                }
        }
        require(trees.isNotEmpty() && trees.size <= 256)
    }
    fun score(features: DoubleArray): Double {
        require(features.size == UrlFeatures.NAMES.size && features.all { it.isFinite() })
        return trees.sumOf { tree ->
            var node = 0
            var depth = 0
            while (tree.features[node] >= 0) {
                check(++depth <= 128) { "Invalid model tree" }
                node = if (features[tree.features[node]].toFloat().toDouble() <= tree.thresholds[node])
                    tree.left[node] else tree.right[node]
            }
            tree.values[node]
        } / trees.size
    }
}
