package com.trevorism.service

class LinearFit {

    final double slope
    final double intercept
    final double r2
    final int count

    private LinearFit(double slope, double intercept, double r2, int count) {
        this.slope = slope
        this.intercept = intercept
        this.r2 = r2
        this.count = count
    }

    double valueAt(double x) {
        intercept + slope * x
    }

    static LinearFit of(List<double[]> points) {
        int n = points.size()
        if (n < 2) {
            return null
        }
        double meanX = points.sum { it[0] } / n
        double meanY = points.sum { it[1] } / n
        double sxx = points.sum { (it[0] - meanX) * (it[0] - meanX) }
        if (sxx == 0d) {
            return null
        }
        double sxy = points.sum { (it[0] - meanX) * (it[1] - meanY) }
        double slope = sxy / sxx
        double intercept = meanY - slope * meanX
        double ssTotal = points.sum { (it[1] - meanY) * (it[1] - meanY) }
        double ssResidual = points.sum { double[] point -> double residual = point[1] - (intercept + slope * point[0]); residual * residual }
        double r2 = ssTotal == 0d ? 1d : 1d - ssResidual / ssTotal
        new LinearFit(slope, intercept, r2, n)
    }
}
