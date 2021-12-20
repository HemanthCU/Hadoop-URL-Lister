import java.io.IOException;
import java.util.StringTokenizer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class URLCount {
    
    /**
     * Mapper using java regex patterrn-matcher classes to separate URLs and
     * create <key,value> pairs
     */
    public static class PatternMatcherMapper
        extends Mapper<Object, Text, Text, IntWritable> {
        private final static IntWritable one = new IntWritable(1);
        private Text url = new Text();
        
        public void map(Object key, Text value, Context context
                ) throws IOException, InterruptedException {
            Pattern pattern = Pattern.compile("href=\"[^\"]*\"");
            Matcher matcher = pattern.matcher(value.toString());

            while (matcher.find()) {
                //Code to crop matched string and remove the href= and double quotes 
                String matchedUrl = new String(matcher.group());
                String justUrl = new String(matchedUrl.substring(6, matchedUrl.length() - 1));
                url.set(justUrl);
                context.write(url, one);
            }
        }
    }

    /**
     * Combiner joins the pairs at the respective mappers before being shuffled
     * to the reducers. We do not apply the filter => (sum > 5) at this stage.
     */
    public static class IntSumCombiner
        extends Reducer<Text,IntWritable,Text,IntWritable> {
        private IntWritable result = new IntWritable();

        public void reduce(Text key, Iterable<IntWritable> values, Context context
                ) throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable val : values) {
                sum += val.get();
            }
            result.set(sum);
            context.write(key, result);
        }
    }   

    /**
     * Reducer sums up the total count for each URL from all the mappers and
     * then applies the filter => (sum > 5) at this stage to get the final result.
     */
    public static class IntSumReducer
        extends Reducer<Text,IntWritable,Text,IntWritable> {
        private IntWritable result = new IntWritable();
        
        public void reduce(Text key, Iterable<IntWritable> values, Context context
                ) throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable val : values) {
                sum += val.get();
            }

            if (sum > 5) {
                result.set(sum);
                context.write(key, result);
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "URL count");
        job.setJarByClass(URLCount.class);
        job.setMapperClass(PatternMatcherMapper.class);
        job.setCombinerClass(IntSumCombiner.class);
        job.setReducerClass(IntSumReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
