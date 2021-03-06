package ca.mcgill.ecse420.a1.matrixMultiplication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import java.util.List;
import java.util.ArrayList;

public class MatrixMultiplication {

    private static final String helpMessage = "\n"
        + "usage: java MatrixMultiplication [-s size] [-p parallelize[, -n numThreads]]\n\n"
        + "    size,        int:     dimensions of all square matrices used in multiplication\n"
        + "                          (must be multiple of 100)\n"
        + "                          (defaults to 2000)\n"
        + "    parallelize, boolean: if true do parallel matrix multiply else do sequential one\n"
        + "                          (defaults to false)\n"
        + "    numThreads,  int:     number of threads to use for parallel matrix multiply\n"
        + "                          (can only supply this arg if parallelize is true)\n"
        + "                          (must be less than size)\n"
        + "                          (defaults to 1)\n\n"
        + "Examples:\n\n"
        + "    java MatrixMultiplication -s 1000 -p true -n 8\n"
        + "    java MatrixMultiplication -p true -n 8\n"
        + "    java MatrixMultiplication -p true\n"
        + "    java MatrixMultiplication -s 1000 -p true\n";

    private static boolean PARALLELIZE = false;
    private static int MATRIX_SIZE     = 2000;
    private static int NUMBER_THREADS  = 1;

    public static void main(String[] args) {
        if (!parseCommandLineArgs(args)) { // set PARALLELIZE,MATRIX_SIZE,NUMBER_THREADS
            System.exit(1);
        }

        double[][] a = Utils.generateRandomMatrix(MATRIX_SIZE, MATRIX_SIZE);
        double[][] b = Utils.generateRandomMatrix(MATRIX_SIZE, MATRIX_SIZE);

        long startTime = System.currentTimeMillis();

        if (PARALLELIZE) {
            parallelMatrixMultiply(a, b);
        } else {
            sequentialMatrixMultiply(a, b);
        }

        System.out.println(System.currentTimeMillis() - startTime);
    }

    /**
     * Returns the result of a sequential matrix multiplication
     * The two matrices are randomly generated
     * @param a is the first matrix
     * @param b is the second matrix
     * @return the result of the multiplication
     *
     * @pre a and b are nxn square matrices
     * */
    public static double[][] sequentialMatrixMultiply(double[][] a, double[][] b) {
        int n = a.length; // assume a.length == b.length
        double[][] c = new double[n][n]; // a . b = c

        // for each column of b
        for (int bj = 0; bj < n; bj++) {

            // get the column from b
            double[] bCol = new double[n];
            for (int bi = 0; bi < n; bi++) {
                bCol[bi] = b[bi][bj];
            }

            // for each row of a
            for (int ai = 0; ai < n; ai++) {

                // dot_product(a row, b column)
                for (int aj = 0; aj < n; aj++) {

                    // to get each value in a cell of c
                    c[ai][bj] += a[ai][aj]*bCol[aj];
                }
            }
        }

        return c;
    }

    /**
     * Returns the result of a concurrent matrix multiplication
     * The two matrices are randomly generated.
     * Column-wise parallelization of the matrix multiplication
     *
     * @param a is the first matrix
     * @param b is the second matrix
     * @return the result of the multiplication
     *
     * @pre a and b are nxn square matrices
     * */
    public static double[][] parallelMatrixMultiply(double[][] a, double[][] b) {
        int n = a.length; // assume a.length == b.length
        double[][] c = new double[n][n]; // a . b = c

        // Each thread will compute 1 or more columns of product matrix c.
        // The column ranges given to each thread are dependent on NUMBER_THREADS.

        double colsPerThread  = n / (double)NUMBER_THREADS;
        int lowColsPerThread  = (int)Math.floor(colsPerThread);
        int highColsPerThread = (int)Math.ceil(colsPerThread);

        double fracColsPerThreadHighToLow = colsPerThread % 1;
        int numThreadsHighColsPerThread   = (int)(fracColsPerThreadHighToLow * NUMBER_THREADS);
        int numThreadsLowColsPerThread    = NUMBER_THREADS-1-numThreadsHighColsPerThread;

        if (fracColsPerThreadHighToLow == 0.0) {
            numThreadsHighColsPerThread = NUMBER_THREADS;
            numThreadsLowColsPerThread  = 0;
            fracColsPerThreadHighToLow  = 1.0;
        }

        List<Callable<Object>> tasks = new ArrayList<Callable<Object>>();

        int colRangeStart = 0;
        int colRangeEnd = 0;
        for (int threadNum = 0; threadNum < NUMBER_THREADS; threadNum++) {
            if (threadNum < numThreadsHighColsPerThread) {
                colRangeEnd = colRangeStart + highColsPerThread;
            } else if (threadNum < numThreadsHighColsPerThread + numThreadsLowColsPerThread) {
                colRangeEnd = colRangeStart + lowColsPerThread;
            } else {
                colRangeEnd = n;
            }

            PartialMatrixMultiplication p =
                new PartialMatrixMultiplication(a,b,c, colRangeStart,colRangeEnd);
            colRangeStart = colRangeEnd;

            tasks.add(Executors.callable(p));
        }

        ExecutorService threadPool = Executors.newFixedThreadPool(NUMBER_THREADS);
        try {
            threadPool.invokeAll(tasks);
            threadPool.shutdown();
            threadPool.awaitTermination(10, TimeUnit.MINUTES);
        } catch(Exception e){
            e.printStackTrace();
            System.exit(1);
        }

        return c;
    }

    /**
     * Sets the values of MATRIX_SIZE, PARALLELIZE, NUMBER_THREADS if the -s, -p, and -n cli
     * args are provided, respectively. Else, leaves those vars set to their defaults.
     *
     * @param args cli args
     * @return true if cli args are parsed successfully, else false for bad cli args
     */
    private static boolean parseCommandLineArgs(String[] args) {
        if (args.length % 2 != 0 || args.length > 6) {
            System.out.println("ERROR: wrong number of args, expecting 2, 4, or 6");
            System.out.println(helpMessage);
            return false;
        }

        boolean matrixSizeIsSet  = false;
        boolean parallelizeIsSet = false;
        boolean numThreadsIsSet  = false;
        for (int i = 0; i < args.length; i+=2) {
            String flag = args[i];
            if (!flag.equals("-s") && !flag.equals("-p") && !flag.equals("-n")) {
                System.out.println("ERROR: bad syntax");
                System.out.println(helpMessage);
                return false;
            }

            String arg = args[i+1];
            if (flag.equals("-s")) {
                if (matrixSizeIsSet) {
                    System.out.println("ERROR: cannot set size twice");
                    System.out.println(helpMessage);
                    return false;
                }
                try {
                    MATRIX_SIZE = Integer.parseInt(arg);
                    matrixSizeIsSet = true;
                } catch(Exception e){
                    System.out.println("ERROR: size must be an integer");
                    System.out.println(helpMessage);
                    return false;
                }
                if (MATRIX_SIZE % 100 != 0) {
                    System.out.println("ERROR: size must be a multiple of 100");
                    System.out.println(helpMessage);
                    return false;
                }
            }
            else if (flag.equals("-p")) {
                if (parallelizeIsSet) {
                    System.out.println("ERROR: cannot set parallelize twice");
                    System.out.println(helpMessage);
                    return false;
                }
                try {
                    if (Boolean.valueOf(arg)) {
                        PARALLELIZE = true;
                    } else if (!arg.equals("false")) {
                        throw new Exception("");
                    }
                    parallelizeIsSet = true;
                } catch(Exception e) {
                    System.out.println("ERROR: parallelize must be a boolean");
                    System.out.println(helpMessage);
                    return false;
                }
            }
            else if (flag.equals("-n")) {
                if (numThreadsIsSet) {
                    System.out.println("ERROR: cannot set numThreads twice");
                    System.out.println(helpMessage);
                    return false;
                }
                if (!parallelizeIsSet || (parallelizeIsSet && !PARALLELIZE)) {
                    System.out.println("ERROR: '-p true' must be provided before '-n numThreads'");
                    System.out.println(helpMessage);
                    return false;
                }
                try {
                    NUMBER_THREADS = Integer.parseInt(arg);
                    numThreadsIsSet = true;
                } catch(Exception e) {
                    System.out.println("ERROR: numThreads must be an integer");
                    System.out.println(helpMessage);
                    return false;
                }
                if (NUMBER_THREADS > MATRIX_SIZE) {
                    System.out.println("ERROR: numThreads must be less than size");
                    System.out.println(helpMessage);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Test the static method sequentialMatrixMultiply(a,b).
     *
     * Multiplies two 2x2 identity matrices and prints the results.
     * Non-exhaustive tests.
     */
    public static void testSequentialMatrixMultiply(int n) {
        double[][] A = Utils.identityMatrix(n);
        double[][] C = sequentialMatrixMultiply(A, A);
        System.out.println(Utils.verifyIdentityMatrix(C, n));
    }

    /**
     * Test the static method parallelMatrixMultiply(a,b).
     *
     * Multiplies two 2x2 identity matrices and prints the results.
     * Non-exhaustive tests.
     */
    public static void testParallelMatrixMultiply(int n) {
        double[][] A = Utils.identityMatrix(n);
        double[][] C = parallelMatrixMultiply(A, A);
        System.out.println(Utils.verifyIdentityMatrix(C, n));
    }
}
