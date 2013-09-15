import argparse
import numpy
from numpy import linalg
import sys

def main(args):
    """Perform PU learning.

    """

    parser = argparse.ArgumentParser(description='Process the command line input for the PU learning.', epilog='Setting -b, --outfrac to a value x' +
        'will cause the top x fraction of the unlabelled proteins to be output as positive, and the others to be output as unlabelled')
    parser.add_argument('dataset', help='the location containing the dataset')
    parser.add_argument('output', help='the location to save the results')
    parser.add_argument('-f', '--features', default='', help='the features to remove (csv)')
    parser.add_argument('-a', '--possim', default=0.5, type=float, help='the fraction of positive similarities to remove (smaller similarities removed)')
    parser.add_argument('-b', '--outfrac', default=0.0, type=float, help='the fraction of ulnabelled proteins to output as positive')
    parser.add_argument('-c', '--unlabsim', default=0.5, type=float, help='the fraction of unlabelled similarities to remove (smaller similarities removed)')
    args = parser.parse_args()

    # Parse the command line arguments.
    dataFileLocation = args.dataset
    resultsLocation = args.output
    featuresToRemove = args.features.split(',')
    fractionPositiveSimilarityToRemove = args.possim
    fractionUnlabelledToConvert = args.outfrac
    fractionUnlabelledSimilarityToRemove = args.unlabsim

    # Parse the file containing the dataset.
    proteinAccsAndClasses = numpy.genfromtxt(dataFileLocation, dtype=None, delimiter='\t', names=True, case_sensitive=True, usecols=[0,-1])
    unlabelledData = data_processing(dataFileLocation, featuresToRemove)
    unlabelledData = unlabelledData.view((numpy.float64, len(unlabelledData.dtype.names)))  # Convert the structured array created by genfromtxt to a regular array of floats.
    numberOfOriginalFeatures = unlabelledData.shape[1]
    unlabelledData = numpy.delete(unlabelledData, [0, numberOfOriginalFeatures - 1], 1)  # Remove the UPAccession and Classification columns.

    # Standardise the data.
    means = numpy.mean(unlabelledData, 0)
    stdDevs = numpy.std(unlabelledData, 0)
    unlabelledData = (unlabelledData - means) / stdDevs

    # Determine the positive and unlabelled observations.
    positiveData = unlabelledData[proteinAccsAndClasses['Classification'] == b'Positive']
    unlabelledData = unlabelledData[proteinAccsAndClasses['Classification'] == b'Unlabelled']
    numberOfFeatures = numberOfOriginalFeatures - 2

    # Determine the pairwise distances (Euclidean) between all positive observations.
    positiveDistances = numpy.zeros((positiveData.shape[0], positiveData.shape[0]))
    sortedPositiveDistances = set([])
    for i in range(0, positiveDistances.shape[0]):
        for j in range(i + 1, positiveDistances.shape[1]):
            norm = numpy.linalg.norm(positiveData[i] - positiveData[j])
            sortedPositiveDistances.add((norm, i, j))
            positiveDistances[i, j] = norm
            positiveDistances[j, i] = norm
    sortedPositiveDistances = sorted(sortedPositiveDistances, key=lambda x : x[0])
    maxPositiveDistance = sortedPositiveDistances[-1][0]
    minPositiveDistance = sortedPositiveDistances[0][0]

    # Set the fractionPositiveSimilarityToRemove smallest simliarities to the largest distance (this will cause them to be set to 0 after the scaling).
    if fractionPositiveSimilarityToRemove > 0:
        numberOfDistancesToRemove = int(len(sortedPositiveDistances) * fractionPositiveSimilarityToRemove)
        maxPositiveDistance = sortedPositiveDistances[-numberOfDistancesToRemove][0]
        for i in sortedPositiveDistances[-numberOfDistancesToRemove:]:
            indexI = i[1]
            indexJ = i[2]
            positiveDistances[indexI, indexJ] = maxPositiveDistance
            positiveDistances[indexJ, indexI] = maxPositiveDistance
    positiveDistances += (numpy.eye(positiveData.shape[0]) * maxPositiveDistance)  # Set the leading diagonal to have the maximum distance.

    # Inversely scale the positive distances so that larger distances get smaller similarities.
    positiveDistances = (maxPositiveDistance - positiveDistances) / (maxPositiveDistance - minPositiveDistance)

    # Determine the distance between each pair of observations (u, p) where u is an unlabelled protein and p a positive one.
    # The distance in cell unlabelledDistances[i, j] is the distance between positive observation i and unlabelled observation j.
    # Columns therefore represent unlabelled observations, and rows the positive ones.
    unlabelledDistances = numpy.zeros((positiveData.shape[0], unlabelledData.shape[0]))
    sortedUnlabelledDistances = set([])
    for i in range(unlabelledDistances.shape[0]):
        for j in range(unlabelledDistances.shape[1]):
            norm = numpy.linalg.norm(positiveData[i] - unlabelledData[j])
            sortedUnlabelledDistances.add((norm, i, j))
            unlabelledDistances[i, j] = norm
    sortedUnlabelledDistances = sorted(sortedUnlabelledDistances, key=lambda x : x[0])
    maxUnlabelledDistance = sortedUnlabelledDistances[-1][0]
    minUnlabelledDistance = sortedUnlabelledDistances[0][0]

    # Set the fractionUnlabelledSimilarityToRemove smallest simliarities to the largest distance (this will cause them to be set to 0 after the scaling).
    if fractionUnlabelledSimilarityToRemove > 0:
        numberOfDistancesToRemove = int(len(sortedUnlabelledDistances) * fractionUnlabelledSimilarityToRemove)
        maxUnlabelledDistance = sortedUnlabelledDistances[-numberOfDistancesToRemove][0]
        for i in sortedUnlabelledDistances[-numberOfDistancesToRemove:]:
            indexI = i[1]
            indexJ = i[2]
            unlabelledDistances[indexI, indexJ] = maxUnlabelledDistance

    # Inversely scale the unlabelled distances so that larger distances get smaller similarities.
    unlabelledDistances = (maxUnlabelledDistance - unlabelledDistances) / (maxUnlabelledDistance - minUnlabelledDistance)

    # Determine the positive likeness each of the unlabelled observations.
    positiveWeightings = numpy.dot(positiveDistances, unlabelledDistances)
    positiveLikeness = numpy.sum(positiveWeightings, 0)
    print(positiveLikeness)

def data_processing(dataFileLocation, featuresToRemove=[]):
    """Process a data file.

    Processes the dataset file while ensuring that only the desired features in the dataset are in the processed dataset.

    The data file is expected to be tab separated with the first line containing the names of the features/columns.
    The restrictions on the naming of the features/columns are:
        1) The column containing the class should be headed with Classification.

    :param dataFileLocation: The location on disk of the dataset.
    :type dataFileLocation: string
    :param featuresToRemove: The features in the dataset that should be removed after the processing.
    :type featuresToRemove: list of strings
    :returns: The processed dataset with the desired feature set.
    :rtype: numpy structured array

    """

    data = numpy.genfromtxt(dataFileLocation, dtype='float64', delimiter='\t', names=True, case_sensitive=True)  # Parse the file containing the dataset.

    # Select which features (if any) should be removed.
    if featuresToRemove:
        data = data[[i for i in data.dtype.names if i not in featuresToRemove]]

    return data

if __name__ == '__main__':
    main(sys.argv)