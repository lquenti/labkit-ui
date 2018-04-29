package net.imglib2.labkit.utils;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.trainable_segmention.RevampUtils;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * @author Matthias Arzt
 */
public class ParallelUtils {

	public static <T> List<Callable<Void>> chunkOperation(Img<T> image, int[] cellDimensions, Consumer<RandomAccessibleInterval<T>> operation) {
		return getCells(new CellGrid(Intervals.dimensionsAsLongArray(image), cellDimensions))
					.map(interval -> (Callable<Void>) (() -> {
						operation.accept(Views.interval(image, interval)); return null;}))
					.collect(Collectors.toList());
	}

	private static Stream<Interval> getCells(CellGrid cellGrid) {
		long numCells = LongStream.of(cellGrid.getGridDimensions()).reduce(1, (a, b) -> a * b);
		return LongStream.range(0, numCells).mapToObj(i -> getCellOfIndex(cellGrid, i));
	}

	private static Interval getCellOfIndex(CellGrid cellGrid, long index) {
		long[] min = new long[cellGrid.numDimensions()];
		int[] dim = new int[cellGrid.numDimensions()];
		cellGrid.getCellDimensions(index, min, dim);
		long[] max = IntStream.range(0, cellGrid.numDimensions()).mapToLong(i -> min[i] + dim[i] - 1).toArray();
		return new FinalInterval(min, max);
	}

	public static List<Callable<Void>> addShowProgress(List<Callable<Void>> chunks) {
		return addProgress(chunks, ProgressConsumer.systemOut());
	}

	public static List<Callable<Void>> addProgress(List<Callable<Void>> chunks, ProgressConsumer progressConsumer) {
		AtomicInteger i = new AtomicInteger(0);
		int n = chunks.size();
		return chunks.stream().map(
				runnable -> (Callable<Void>) (() -> {
					runnable.call();
					progressConsumer.showProgress(i.incrementAndGet(), n);
					return null;
				})
		).collect(Collectors.toList());
	}

	public static void executeInParallel(ExecutorService executor, List<Callable<Void>> collection) {
		RevampUtils.wrapException(() -> {
			executor.invokeAll(collection).forEach(future -> {
				try {
					future.get();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
			});
		});
	}
}
