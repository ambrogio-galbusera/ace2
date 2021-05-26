using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;

namespace DataSetConverter
{
    class Program
    {
        private const string inputPath = "c:\\source\\AG\\SustainTheWorld\\SisFall_dataset";
        private const string outputPath = "c:\\source\\AG\\SustainTheWorld\\Raw";
        private const string output2Path = "c:\\source\\AG\\SustainTheWorld\\Raw2";
        private const string outputFullPath = "c:\\source\\AG\\SustainTheWorld\\Full";
        private const int subSampling = 4;
        private const int maxSamples = 300;
        private const double gThreshold = 1.4;
        private const double degThreshold = 20.0/100.0;
        private const double prePeakDuration = 1; // seconds
        private static int maxPrevLines = (int)Math.Ceiling(prePeakDuration * (200 / subSampling));

        static double ConvertAccel(double x)
        {
            return ((2.0*8.0)*x)/Math.Pow(2,14);
        }

        static double ConvertAccelNorm(double x, double y, double z)
        {
            return Math.Sqrt(Math.Pow(ConvertAccel(x), 2) + Math.Pow(ConvertAccel(y), 2) + Math.Pow(ConvertAccel(z), 2));
        }

        static double ConvertGyro(double x)
        {
            return ((2.0 * 2000.0) * (x/100.0)) / Math.Pow(2, 16);
        }

        static double ConvertGyroNorm(double x, double y, double z)
        {
            return Math.Sqrt(Math.Pow(ConvertGyro(x), 2) + Math.Pow(ConvertGyro(y), 2) + Math.Pow(ConvertGyro(z), 2));
        }

        static void Main(string[] args)
        {
            DirectoryInfo root = new DirectoryInfo(inputPath);
            int cntr = 1;

            // get all directories (format is SAxx)
            DirectoryInfo[] dis = root.GetDirectories();
            foreach (DirectoryInfo di in dis)
            {
                // load all txt files in the folder
                FileInfo[] fis = di.GetFiles("*.txt");
                foreach (FileInfo fi in fis)
                {
                    // filename format is <ADL>_<SUBJ>_<PROG>.txt
                    string act = fi.Name.Split(new char[] { '_' })[0];
                    // data format is ax,ay,az,gx,gy,gz
                    // Acceleration[g]: [(2 * Range)/ (2 ^ Resolution)]*AD
                    // Angular velocity[°/ s]: [(2 * Range)/ (2 ^ Resolution)]*RD
                    StreamReader sr = new StreamReader(fi.FullName);
                    StreamWriter sw = new StreamWriter(Path.Combine(outputPath, string.Format("{0}_{1:D4}.csv", act, cntr)));
                    StreamWriter sw1 = new StreamWriter(Path.Combine(outputFullPath, string.Format("{0}_{1:D4}.csv", act, cntr)));
                    StreamWriter sw2 = new StreamWriter(Path.Combine(output2Path, string.Format("{0}_{1:D4}.csv", act, cntr)));
                    cntr++;

                    sw.WriteLine("aX;aY;aZ;gX;gY;gZ");
                    sw1.WriteLine("aX;aY;aZ;gX;gY;gZ");
                    sw2.WriteLine("a;g");

                    List<string> prevLines = new List<string>();
                    
                    bool peakFound = false;
                    int numLines = 0;
                    int numSamples = 0;
                    while (!sr.EndOfStream)
                    {
                        string line = sr.ReadLine().Replace(";",",");
                        string[] fields = line.Split(new char[] { ','}) ;
                        if (fields.Length < 9)
                            continue;

                        numSamples++;
                        if (numSamples < subSampling)
                            continue;

                        string outLine = string.Format("{0};{1};{2};{3};{4};{5}",
                            ConvertAccel(Convert.ToDouble(fields[6])).ToString("0.###"),
                            ConvertAccel(Convert.ToDouble(fields[7])).ToString("0.###"),
                            ConvertAccel(Convert.ToDouble(fields[8])).ToString("0.###"),
                            ConvertGyro(Convert.ToDouble(fields[3])).ToString("0.###"),
                            ConvertGyro(Convert.ToDouble(fields[4])).ToString("0.###"),
                            ConvertGyro(Convert.ToDouble(fields[5])).ToString("0.###"));
                        outLine = outLine.Replace(",", ".");
                        sw1.WriteLine(outLine);

                        if (!peakFound)
                        {
                            double aX = ConvertAccel(Convert.ToDouble(fields[6]));
                            double aY = ConvertAccel(Convert.ToDouble(fields[7]));
                            double aZ = ConvertAccel(Convert.ToDouble(fields[8]));

                            double gX = ConvertGyro(Convert.ToDouble(fields[3]));
                            double gY = ConvertGyro(Convert.ToDouble(fields[4]));
                            double gZ = ConvertGyro(Convert.ToDouble(fields[5]));

                            double aNorm = Math.Sqrt((aX * aX) + (aY * aY) + (aZ * aZ));
                            if ((aNorm > gThreshold) || (gX > degThreshold) || (gY > degThreshold) || (gZ > degThreshold))
                            {
                                peakFound = true;
                                // save prevLines to output file
                                for (int i=0; i<prevLines.Count; i++)
                                {
                                    line = prevLines[i];
                                    fields = line.Split(new char[] { ',' });
                                    if (fields.Length < 9)
                                        continue;

                                    outLine = string.Format("{0};{1};{2};{3};{4};{5}",
                                        ConvertAccel(Convert.ToDouble(fields[6])).ToString("0.###"),
                                        ConvertAccel(Convert.ToDouble(fields[7])).ToString("0.###"),
                                        ConvertAccel(Convert.ToDouble(fields[8])).ToString("0.###"),
                                        ConvertGyro(Convert.ToDouble(fields[3])).ToString("0.###"),
                                        ConvertGyro(Convert.ToDouble(fields[4])).ToString("0.###"),
                                        ConvertGyro(Convert.ToDouble(fields[5])).ToString("0.###"));
                                    outLine = outLine.Replace(",", ".");
                                    sw.WriteLine(outLine);

                                    outLine = string.Format("{0};{1}",
                                        ConvertAccelNorm(Convert.ToDouble(fields[6]), Convert.ToDouble(fields[7]), Convert.ToDouble(fields[8])).ToString("0.###"),
                                        ConvertGyroNorm(Convert.ToDouble(fields[3]), Convert.ToDouble(fields[4]), Convert.ToDouble(fields[5])).ToString("0.###"));
                                    outLine = outLine.Replace(",", ".");
                                    sw2.WriteLine(outLine);

                                    numLines++;
                                }
                            }
                            else
                            {
                                // append line to prevLines
                                if (prevLines.Count >= maxPrevLines)
                                {
                                    // shift
                                    prevLines.RemoveAt(0);
                                }

                                prevLines.Add(line);
                            }
                        }
                        else
                        {
                            outLine = string.Format("{0};{1};{2};{3};{4};{5}",
                                ConvertAccel(Convert.ToDouble(fields[6])).ToString("0.###"),
                                ConvertAccel(Convert.ToDouble(fields[7])).ToString("0.###"),
                                ConvertAccel(Convert.ToDouble(fields[8])).ToString("0.###"),
                                ConvertGyro(Convert.ToDouble(fields[3])).ToString("0.###"),
                                ConvertGyro(Convert.ToDouble(fields[4])).ToString("0.###"),
                                ConvertGyro(Convert.ToDouble(fields[5])).ToString("0.###"));
                            outLine = outLine.Replace(",", ".");
                            sw.WriteLine(outLine);

                            outLine = string.Format("{0};{1}",
                                ConvertAccelNorm(Convert.ToDouble(fields[6]), Convert.ToDouble(fields[7]), Convert.ToDouble(fields[8])).ToString("0.###"),
                                ConvertGyroNorm(Convert.ToDouble(fields[3]), Convert.ToDouble(fields[4]), Convert.ToDouble(fields[5])).ToString("0.###"));
                            outLine = outLine.Replace(",", ".");
                            sw2.WriteLine(outLine);

                            numLines++;
                        }

                        numSamples = 0;
                        if (numLines >= maxSamples)
                            break;
                    }

                    string outEmptyLine = "0;0;0;0;0;0";
                    string out2EmptyLine = "0;0";
                    while (numLines < maxSamples)
                    {
                        sw.WriteLine(outEmptyLine);
                        sw2.WriteLine(out2EmptyLine);
                        numLines++;
                    }

                    sw.Close();
                    sw1.Close();
                    sw2.Close();
                }
            }
        }
    }
}
