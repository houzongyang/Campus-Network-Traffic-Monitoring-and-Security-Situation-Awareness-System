package com.campus.network.algorithm;

import java.util.Collection;

/**
 * 算法模块通用数学工具，集中维护信息熵、S 形函数、截断和四舍五入等基础公式。
 * 中文说明：Shannon 信息熵、S 形函数、截断和四舍五入均为通用数学/统计工具；
 * 本项目仅复用这些基础公式服务于四套融合算法，不将基础公式本身宣称为原创。
 */
public final class AlgorithmMathUtils {

    private static final double LOG_2 = Math.log(2);

    private AlgorithmMathUtils() {
    }

    /**
     * Shannon 信息熵：按各类别概率乘以其二进制对数后的负和计算，用于衡量协议、端口等离散分布的离散程度。
     */
    public static double shannonEntropy(Collection<Long> counts) {
        double total = counts.stream()
                .filter(count -> count != null && count > 0)
                .mapToLong(Long::longValue)
                .sum();
        if (total <= 0.0) {
            return 0.0;
        }

        double entropy = 0.0;
        for (Long count : counts) {
            if (count != null && count > 0) {
                double probability = count / total;
                entropy -= probability * (Math.log(probability) / LOG_2);
            }
        }
        return entropy;
    }

    /**
     * 将正负偏离都视为异常信号的 S 形映射，偏移量用于抑制较低标准分偏离。
     */
    public static double absoluteSigmoid(double value, double offset) {
        return 1.0 / (1.0 + Math.exp(-Math.abs(value) + offset));
    }

    /**
     * 标准 S 形映射，用于把连续评分压缩到 [0, 1] 区间。
     */
    public static double sigmoid(double value) {
        return 1.0 / (1.0 + Math.exp(-value));
    }

    /**
     * 截断到 [0, 1]，避免归一化特征越界影响综合评分。
     */
    public static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }
}
