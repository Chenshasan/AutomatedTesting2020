import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * @ClassName testSelection
 * @Description TODO
 * @Author 李甘霖
 * @Date 2020/11/1711:20
 **/
public class testSelection {
    public static void addScope(File file,AnalysisScope scope) throws InvalidClassFileException {
        File[] fs=file.listFiles();
        for(File f: fs){
            if(f.isDirectory()){
                addScope(f,scope);
            }
            else{
                if(f.getName().endsWith(".class")){
                    scope.addClassFileToScope(ClassLoaderReference.Application,f);
                }
            }
        }
    }

    public static void addTestClass(File file,ArrayList<String> testClass) throws InvalidClassFileException {
        File[] fs=file.listFiles();
        for(File f: fs){
            if(f.isDirectory()){
                addTestClass(f,testClass);
            }
            else{
                if(f.getName().endsWith(".class")){
                    testClass.add(f.getName());
                }
            }
        }
    }

    public static void main(String[] args) throws CancelException, IOException, InvalidClassFileException, ClassHierarchyException {
        /* 省略构建分析域（AnalysisScope）对象scope的过程 */
        boolean classLevel=false;
//        if(args.length<3){
//            System.out.println("必须有三个参数");
//        }
//        if(args[0].equals("-c")){
//            classLevel=true;
//        }
//        if(args[0].equals("-m")){
//            classLevel=false;
//        }
//        String path=args[1];
//        String changeInfoPath=args[2];
        String path="D:\\ClassicAutomatedTesting\\5-MoreTriangle\\target";
        String changeInfoPath="D:\\ClassicAutomatedTesting\\5-MoreTriangle\\data\\change_info.txt";

        //读取变更信息内容
        FileInputStream inputStream = new FileInputStream(changeInfoPath);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        ArrayList<String> changeInfo=new ArrayList<String>();
        String str = null;
        while((str = bufferedReader.readLine()) != null) {
            changeInfo.add(str);
        }
        inputStream.close();
        bufferedReader.close();

        //初始化scope
        ClassLoader classLoader = testSelection.class.getClassLoader();
        AnalysisScope scope = AnalysisScopeReader.readJavaScope("scope.txt", /*Path to scope file*/ new File("exclusion.txt"), /*Path to exclusion file*/ classLoader);
        //扫描文件夹中所有的.class类，将其添加到scope中
        File file=new File(path);
        addScope(file,scope);
        //System.out.println(scope);
        //System.out.println("--------------------------------------------------------------");

        //此处从test-classes中读取测试类类名
        File file1=new File(path+"\\test-classes");
        ArrayList<String> testClass=new ArrayList<String>();
        addTestClass(file1,testClass);
//        for(String s:testClass){
//            System.out.println(s);
//        }
//        System.out.println("--------------------------------------------------------------");

        // 1.生成类层次关系对象
        ClassHierarchy cha = null;
        cha = ClassHierarchyFactory.makeWithRoot(scope);
        // 2.生成进入点
        Iterable<Entrypoint> eps = new AllApplicationEntrypoints(scope, cha);
        // 3.利用CHA算法构建调用图
        CallGraph cg = new CHACallGraph(cha);
        ((CHACallGraph) cg).init(eps);


        //legal中存储cg中所有在项目内的节点，排出一些基本类的节点
        ArrayList<CGNode> legal=new ArrayList<CGNode>();
        //reachable中存储本次受影响的所有节点
        ArrayList<CGNode> reachable=new ArrayList<CGNode>();
        //workList中存储已受影响，待处理的节点
        ArrayList<CGNode> workList=new ArrayList<CGNode>();
        //result中存储最终的测试结果节点
        ArrayList<CGNode> result=new ArrayList<CGNode>();
        ArrayList<String> depend=new ArrayList<String>();

        // 4.遍历cg中所有的节点
        for(CGNode node: cg) {
            // node中包含了很多信息，包括类加载器、方法信息等，这里只筛选出需要的信息
            if(node.getMethod() instanceof ShrikeBTMethod) {
                // node.getMethod()返回一个比较泛化的IMethod实例，不能获取到我们想要的信息
                // 一般地，本项目中所有和业务逻辑相关的方法都是ShrikeBTMethod对象
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                // 使用Primordial类加载器加载的类都属于Java原生类，我们一般不关心。
                if("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    // 获取声明该方法的类的内部表示
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    // 获取方法签名
                    String signature = method.getSignature();
                    if(changeInfo.contains(classInnerName + " " + signature)){
                        workList.add(node);
                    }
                    legal.add(node);
                    System.out.println(classInnerName + " " + signature); }
            }
            else {
                System.out.println(String.format("'%s'不是一个ShrikeBTMethod：%s",node.getMethod(), node.getMethod().getClass()));
            }
        }

        for(CGNode node: cg) {
            // node中包含了很多信息，包括类加载器、方法信息等，这里只筛选出需要的信息
            if(node.getMethod() instanceof ShrikeBTMethod) {
                // node.getMethod()返回一个比较泛化的IMethod实例，不能获取到我们想要的信息
                // 一般地，本项目中所有和业务逻辑相关的方法都是ShrikeBTMethod对象
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                // 使用Primordial类加载器加载的类都属于Java原生类，我们一般不关心。
                if("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    // 获取声明该方法的类的内部表示
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    // 获取方法签名
                    String signature = method.getSignature();
                    for (Iterator<CGNode> it = cg.getSuccNodes(node); it.hasNext(); ) {
                        CGNode node1 = it.next();
                        if(node1.getMethod() instanceof ShrikeBTMethod && legal.contains(node1)){
                            ShrikeBTMethod method1 = (ShrikeBTMethod) node1.getMethod();
                            if(!classLevel){
                                depend.add("\""+method.getSignature()+"\""+" -> "+"\""+method1.getSignature()+"\"\n");
                            }
                            else{
                                String a="\""+method.getDeclaringClass().getName().toString()+"\""+" -> "+"\""+method1.getDeclaringClass().getName().toString()+"\"\n";
                                if(!depend.contains(a)){
                                    depend.add(a);
                                }
                            }
                        }
                    }
                    if(changeInfo.contains(classInnerName + " " + signature)){
                        workList.add(node);
                    }
                    legal.add(node);
                    System.out.println(classInnerName + " " + signature); }
            }
            else {
                System.out.println(String.format("'%s'不是一个ShrikeBTMethod：%s",node.getMethod(), node.getMethod().getClass()));
            }
        }
        if(classLevel){
            System.out.println("workList--------------------------------------------------------------");
            ArrayList<CGNode> workList1=workList;
            workList=new ArrayList<CGNode>();
            for(CGNode node:workList1){
                if(node.getMethod() instanceof ShrikeBTMethod) {
                    ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    for(CGNode node1:legal){
                        if(node1.getMethod() instanceof ShrikeBTMethod) {
                            ShrikeBTMethod method1 = (ShrikeBTMethod) node1.getMethod();
                            String classInnerName1 = method1.getDeclaringClass().getName().toString();
                            if(classInnerName.equals(classInnerName1)){
                                workList.add(node1);
                            }
                            System.out.println(classInnerName1 + " " + method1.getSignature());
                        }
                    }
                }
            }
        }

        //判断变更信息影响了的具体方法
        while(workList.size()>0){
            if(!reachable.contains(workList.get(0))){
                reachable.add(workList.get(0));
                for (Iterator<CGNode> it = cg.getPredNodes(workList.get(0)); it.hasNext(); ) {
                    CGNode node = it.next();
                    if(legal.contains(node)){
                        workList.add(node);
                    }
                }
            }
            workList.remove(workList.get(0));
        }

        //输出reachable数组并将受影响的测试方法添加到result数组中
        System.out.println("--------------------------------------------------------------");
        for(CGNode node:reachable){
            if(node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod)  node.getMethod();
                String classInnerName = method.getDeclaringClass().getName().toString();
                String[] pieces=classInnerName.split("/");
                String name=pieces[pieces.length-1]+".class";
                if(testClass.contains(name)){
                    result.add(node);
                }
                System.out.println(method.getSignature());
            }
        }

        System.out.println("result--------------------------------------------------------------");
        File file2=null;
        if(classLevel){
            file2=new File("./selection-class.txt");
        }
        else{
            file2=new File("./selection-method.txt");
        }
        file2.createNewFile();
        FileWriter fileWriter = new FileWriter(file2);
        for(CGNode node:result){
            if(node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                String classInnerName = method.getDeclaringClass().getName().toString();
                String signature = method.getSignature();
                System.out.println(classInnerName + " " + signature);
                fileWriter.write(classInnerName + " " + signature+"\n");
            }
        }
        fileWriter.close();

        File file3;
        if(!classLevel){
            file3=new File("./method-MoreTriangle.dot");
        }
        else{
            file3=new File("./class-MoreTriangle.dot");
        }
        FileWriter fileWriter1 = new FileWriter(file3);
        for(String s:depend){
            fileWriter1.write(s);
        }
        fileWriter1.close();
    }
}

