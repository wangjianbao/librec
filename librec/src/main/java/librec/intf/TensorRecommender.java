// Copyright (C) 2014-2015 Guibing Guo
//
// This file is part of LibRec.
//
// LibRec is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// LibRec is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package librec.intf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import librec.data.Configuration;
import librec.data.MatrixEntry;
import librec.data.SparseMatrix;
import librec.data.SparseTensor;
import librec.data.TensorEntry;
import librec.util.FileIO;
import librec.util.Logs;
import librec.util.Strings;

/**
 * Interface for tensor recommenders
 * 
 * @author Guo Guibing
 *
 */

@Configuration("factors, lRate, maxLRate, reg, iters, boldDriver")
public class TensorRecommender extends IterativeRecommender {

	/* for all tensors */
	protected static SparseTensor rateTensor;
	protected static int numDimensions, userDimension, itemDimension;
	protected static int[] dimensions;

	/* for a specific recommender */
	protected SparseTensor trainTensor, testTensor;

	static {
		rateTensor = rateDao.getRateTensor();
		numDimensions = rateTensor.numDimensions();
		dimensions = rateTensor.dimensions();

		userDimension = rateTensor.getUserDimension();
		itemDimension = rateTensor.getItemDimension();
	}

	public TensorRecommender(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) throws Exception {
		super(trainMatrix, testMatrix, fold);

		// construct train and test data
		trainTensor = rateTensor.clone();
		testTensor = new SparseTensor(dimensions);
		testTensor.setUserDimension(userDimension);
		testTensor.setItemDimension(itemDimension);

		for (MatrixEntry me : testMatrix) {
			int u = me.row();
			int i = me.column();

			List<Integer> indices = rateTensor.getIndices(u, i);

			for (int index : indices) {
				int[] keys = rateTensor.keys(index);
				testTensor.set(rateTensor.value(index), keys);
				trainTensor.remove(keys);
			}
		}
	}

	@Override
	protected void evalRatings() throws Exception {
		List<String> preds = null;
		String toFile = null;
		if (isResultsOut) {
			preds = new ArrayList<String>(1500);
			preds.add("# userId itemId rating prediction"); // optional: file header
			toFile = tempDirPath + algoName + "-rating-predictions" + foldInfo + ".txt"; // the output-file name
			FileIO.deleteFile(toFile); // delete possibly old files
		}

		double sum_maes = 0, sum_mses = 0, sum_r_maes = 0, sum_r_rmses = 0;
		int numCount = 0, numPEs = 0;
		for (TensorEntry te : testTensor) {
			double rate = te.get();

			int u = te.key(userDimension);
			int j = te.key(itemDimension);

			if (!isTestable(u, j))
				continue;

			double pred = predict(te.keys(), true);
			if (Double.isNaN(pred))
				continue;

			measures.updateRatingMetrics(u, j, pred, rate, this);

			numCount++;

			// output predictions
			if (isResultsOut) {
				// restore back to the original user/item id
				preds.add(rateDao.getUserId(u) + " " + rateDao.getItemId(j) + " " + rate + " " + (float) pred);
				if (preds.size() >= 1000) {
					FileIO.writeList(toFile, preds, true);
					preds.clear();
				}
			}
		}

		if (isResultsOut && preds.size() > 0) {
			FileIO.writeList(toFile, preds, true);
			Logs.debug("{}{} has written rating predictions to {}", algoName, foldInfo, toFile);
		}

		measures.computeRatingMetrics(numCount);
	}

	protected double predict(int[] keys, boolean bound) throws Exception {
		double pred = predict(keys);

		if (bound) {
			if (pred > maxRate)
				pred = maxRate;
			if (pred < minRate)
				pred = minRate;
		}

		return pred;
	}

	protected double predict(int[] keys) throws Exception {
		return predict(keys[userDimension], keys[itemDimension]);
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { numFactors, initLRate, maxLRate, reg, numIters, isBoldDriver });
	}

}
