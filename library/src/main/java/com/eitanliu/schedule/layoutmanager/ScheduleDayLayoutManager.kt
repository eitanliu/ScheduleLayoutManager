package com.eitanliu.schedule.layoutmanager

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.joda.time.Period


class ScheduleDayLayoutManager(
    val columnMargin: Int = 0,
    var itemSpace: Int = 0,
    var itemSpaceMin: Int = 0,
    val itemMarginTop: Int = 0,
    val itemMarginBottom: Int = 0,
    val itemMarginStart: Int = 0,
    val itemMarginEnd: Int = 0,
    private val adapter: IScheduleAdapter? = null,
) : RecyclerView.LayoutManager() {

    // val itemSpaceMin = 28
    // val itemSpaceMin get() = (0.62 * itemSpace).toInt()

    private val TAG = this::class.java.simpleName

    private var view: RecyclerView? = null

    private val viewAdapter: RecyclerView.Adapter<*>? get() = view?.adapter

    private val scheduleAdapter: IScheduleAdapter?
        get() = adapter ?: viewAdapter as? IScheduleAdapter

    val itemWidth get() = width - paddingStart - paddingEnd - itemMarginStart - itemMarginEnd + columnMargin

    init {
        isMeasurementCacheEnabled = false
    }

    private val items: Sequence<IScheduleItem>
        get() = sequence {
            val adapter = scheduleAdapter ?: return@sequence
            yieldAll(adapter.scheduleItems)
        }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        this.view = view
    }

    override fun onDetachedFromWindow(view: RecyclerView?, recycler: RecyclerView.Recycler?) {
        super.onDetachedFromWindow(view, recycler)
        this.view = null
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {

        if (state.itemCount == 0) {
            removeAndRecycleAllViews(recycler)
            return
        }

        //不支持预测动画
        if (state.isPreLayout) return

        val adapter = scheduleAdapter ?: return

        measureItemColumn()

        detachAndScrapAttachedViews(recycler)

        for (position in 0 until state.itemCount) {

            val child = recycler.getViewForPosition(position)
            val item = adapter.getScheduleItem(position)
            item.updateLayoutParams(child)

            addView(child, position)
            measureChildWithMargins(child, 0, 0)

            if (!state.isMeasuring) {
                val layoutParams = child.layoutParams as RecyclerView.LayoutParams
                val width = getDecoratedMeasuredWidth(child)
                val height = getDecoratedMeasuredHeight(child)
                layoutParams.apply {

                    layoutDecorated(
                        child,
                        marginStart,
                        topMargin,
                        marginStart + width,
                        topMargin + height
                    )
                }
            }
        }


    }

    override fun onLayoutCompleted(state: RecyclerView.State) {
        super.onLayoutCompleted(state)
    }

    private fun measureItemColumn() {
        val rowList = arrayListOf<Row>() // 行数据
        //val columnList = arrayListOf<Column>() // 列数据
        //val sortList = itemViewArray.sortedByDescending { (it.endPeriod - it.startPeriod).toStandardDuration() }
        val sortList = items.sortedWith(
            compareBy<IScheduleItem> { it.startPeriod.toStandardDuration() }
                .thenByDescending { (it.endPeriod - it.startPeriod).toStandardDuration() }
        )

        sortList.forEach { item ->

            /**
             * 1. 判断列是否存在不存在新建
             * 2. 比较item时间和列时间
             * 2.1 在列之内比较下一列
             * 2.2 在列之外添加到列
             */
            var rowIndex = 0
            var columnIndex = 0
            while (true) {

                // 判断行是否存在
                if (rowIndex >= rowList.size) {
                    val column = Column(item.startPeriod, item.endPeriod, arrayListOf(item))
                    val row = Row(item.startPeriod, item.endPeriod, arrayListOf(column))
                    rowList.add(row)
                    break
                }


                val row = rowList[rowIndex]
                val rowStart = row.startPeriod.toDistance()
                var rowEnd = row.endPeriod.toDistance()
                if (rowEnd - rowStart < itemSpaceMin) {
                    rowEnd = rowStart + itemSpaceMin
                }
                val rowRange = rowStart..rowEnd

                val itemStart = item.startPeriod.toDistance()
                var itemEnd = item.endPeriod.toDistance()
                if (itemEnd - itemStart < itemSpaceMin) {
                    itemEnd = itemStart + itemSpaceMin
                }
                val itemRange = itemStart..itemEnd

                val equalStartEnd = (itemStart == rowEnd || itemEnd == rowStart)
                val inRow = (itemStart in rowRange || itemEnd in rowRange)
                if ((inRow && !equalStartEnd).not()) {
                    rowIndex++
                    continue
                }

                val columnList = row.columnList
                if (columnIndex < columnList.size) {
                    val column = columnList[columnIndex]
                    val columnStart = column.startPeriod.toDistance()
                    var columnEnd = column.endPeriod.toDistance()
                    if (columnEnd - columnStart < itemSpaceMin) {
                        columnEnd = columnStart + itemSpaceMin
                    }
                    val columnRange = columnStart..columnEnd

                    val isBreak = run {

                        column.viewList.forEach { prev ->

                            val prevStart = prev.startPeriod.toDistance()
                            var prevEnd = prev.endPeriod.toDistance()
                            if (prevEnd - prevStart < itemSpaceMin) {
                                prevEnd = prevStart + itemSpaceMin
                            }
                            val prevRange = prevStart..prevEnd

                            val equalStartEnd = (itemStart == prevEnd || itemEnd == prevStart)
                            val inColumn = (itemStart in prevRange || itemEnd in prevRange)

                            // Log.e(
                            //     TAG,
                            //     "columnRange " +
                            //         "${zeroTime.plus(item.startPeriod)}, ${zeroTime.plus(item.endPeriod)}, " +
                            //         "${zeroTime.plus(prev.startPeriod)}, ${zeroTime.plus(prev.endPeriod)}, " +
                            //         "$inColumn $equalStartEnd, $rowIndex $columnIndex "
                            // )
                            if (inColumn && !equalStartEnd) {
                                columnIndex++
                                return@run false
                            }
                        }
                        column.viewList.add(item)

                        if (columnEnd < itemEnd) column.endPeriod = item.endPeriod
                        if (columnStart > itemStart) column.startPeriod = item.startPeriod
                        if (rowEnd < itemEnd) row.endPeriod = item.endPeriod
                        if (rowStart > itemStart) row.startPeriod = item.startPeriod
                        return@run true
                    }
                    if (isBreak) break
                } else {
                    columnList.add(Column(item.startPeriod, item.endPeriod, arrayListOf(item)))
                    if (rowEnd < itemEnd) row.endPeriod = item.endPeriod
                    if (rowStart > itemStart) row.startPeriod = item.startPeriod

                    break
                }
            }

        }

        /**
         * 1. 遍历列数据下所有item，赋值开始列和总列数
         * 2. item 和下一列起止时间比较
         * 2.1 在时间内结束列为该列
         * 2.2 不再继续比较下一列
         */
        rowList.forEach { row ->
            val columnList = row.columnList
            val columnListSize = columnList.size
            columnList.forEachIndexed { index, column ->

                column.viewList.forEach { item ->
                    item.columnStart = index
                    item.columnEnd = index
                    item.columnSize = columnListSize
                    val itemStart = item.startPeriod.toDistance()
                    var itemEnd = item.endPeriod.toDistance()
                    if (itemEnd - itemStart < itemSpaceMin) {
                        itemEnd = itemStart + itemSpaceMin
                    }
                    val itemRange = itemStart..itemEnd

                    for (next in (index + 1) until columnListSize) {
                        val nextColumn = columnList[next]
                        val isBreak = run {
                            nextColumn.viewList.forEach { nextItem ->

                                val columnStart = nextItem.startPeriod.toDistance()
                                var columnEnd = nextItem.endPeriod.toDistance()
                                if (columnEnd - columnStart < itemSpaceMin) {
                                    columnEnd = columnStart + itemSpaceMin
                                }
                                val columnRange = columnStart..columnEnd

                                val equalStartEnd =
                                    (itemStart == columnEnd || itemEnd == columnStart)
                                val inColumn = itemStart in columnRange || itemEnd in columnRange
                                    || columnStart in itemRange || columnEnd in itemRange
                                if (inColumn && !equalStartEnd) {
                                    return@run true
                                }
                            }
                            return@run false
                        }
                        if (isBreak) break
                        item.columnEnd = next
                    }
                }
            }
        }

    }

    /**
     * Desc: 计算Item位置
     * <p>
     * @author eitanliu
     * @since 2020-07-26
     * @receiver TimeTableItem
     */
    private fun IScheduleItem.updateLayoutParams(view: View) {
        // Log.e(TAG, run { "columnMeasure $columnStart, $columnEnd, $columnSize, $columnMeasure, $itemWidth" })
        val layoutParams = view.layoutParams as RecyclerView.LayoutParams
        layoutParams.topMargin = timeToDistance(startPeriod).toInt()
        val height = timeToDistance(endPeriod - startPeriod, 0).toInt()
        layoutParams.height = Math.max(height, itemSpaceMin)
        //layoutParams.marginStart = itemMarginStart.toInt()
        //layoutParams.marginEnd = itemMarginEnd.toInt()
        val marginStart = itemMarginStart + itemWidth * columnMeasureStart
        val width = itemWidth * columnMeasureWidth - columnMargin
        layoutParams.marginStart = marginStart.toInt()
        layoutParams.width = width.toInt()
        layoutParams.resolveLayoutDirection(layoutParams.layoutDirection)
    }

    /**
     * Desc: 时间换算距离
     * <p>
     * @author eitanliu
     * @since 2020-07-22
     * @param offset Int
     * @return Float
     */
    private fun Period.toDistance(offset: Int = itemMarginTop) = timeToDistance(this, offset)

    /**
     * Desc: 时间换算距离
     * <p>
     * @author eitanliu
     * @since 2020-07-22
     * @param time Period
     * @param offset Int
     * @return Float
     */
    private fun timeToDistance(time: Period, offset: Int = itemMarginTop) = run {
        time.days * 24 * itemSpace + time.hours * itemSpace + time.minutes / 60f * itemSpace + offset
    }

    class Row(
        var startPeriod: Period,
        var endPeriod: Period,
        var columnList: MutableList<Column>
    )

    class Column(
        var startPeriod: Period,
        var endPeriod: Period,
        var viewList: MutableList<IScheduleItem>
    )
}