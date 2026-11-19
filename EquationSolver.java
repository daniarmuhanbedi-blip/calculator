import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Однопеременный учебный решатель. Запуск: java EquationSolver.java */
public class EquationSolver {
    private static final double EPS = 1e-9;
    private static final int GRID = 40000;
    private static final double RANGE = 1000;

    interface Node { double at(double x); boolean hasX(); }
    record Num(double value) implements Node { public double at(double x){return value;} public boolean hasX(){return false;} }
    record X() implements Node { public double at(double x){return x;} public boolean hasX(){return true;} }
    record Sym(String name) implements Node { public double at(double x){return Double.NaN;} public boolean hasX(){return true;} }
    record Unary(char op, Node n) implements Node {
        public double at(double x){return op=='-'?-n.at(x):n.at(x);} public boolean hasX(){return n.hasX();}
    }
    record Bin(char op, Node a, Node b) implements Node {
        public double at(double x){double l=a.at(x),r=b.at(x);return switch(op){case '+'->l+r;case '-'->l-r;case '*'->l*r;case '/'->l/r;default->Math.pow(l,r);};}
        public boolean hasX(){return a.hasX()||b.hasX();}
    }
    record Fn(String name, Node arg) implements Node {
        public double at(double x){double v=arg.at(x);return switch(name){case "sin"->Math.sin(v);case "cos"->Math.cos(v);case "tan"->Math.tan(v);case "sqrt"->Math.sqrt(v);case "ln"->Math.log(v);case "log"->Math.log10(v);case "exp"->Math.exp(v);case "abs"->Math.abs(v);default->Double.NaN;};}
        public boolean hasX(){return arg.hasX();}
    }

    static String clean(String s) {
        s=s.replace('х','x').replace('Х','x').replace('×','*').replace('·','*').replace('÷','/')
           .replace('−','-').replace('–','-').replace('π','p').replace("≥",">=").replace("≤","<=")
           .replace("≠","!=").replace("²","^2").replace("³","^3").replace("¹","^1")
           .replace("⁰","^0").replace("⁴","^4").replace("⁵","^5").replace("⁶","^6")
           .replace("⁷","^7").replace("⁸","^8").replace("⁹","^9");
        s=s.replaceAll("(?i)\\\\left|\\\\right","").replace("\\\\cdot","*").replace("\\\\times","*")
           .replace("\\\\ge",">=").replace("\\\\le","<=").replace("\\\\pi","pi").replace("\\\\sqrt","sqrt");
        s=s.replace('{','(').replace('}',')').replaceAll("\\s+","").replace(',', '.');
        return convertFractions(s);
    }
    static String convertFractions(String s) {
        int i=s.indexOf("\\frac"); if(i<0)return s;
        int a=i+5; while(a<s.length()&&s.charAt(a)==' ')a++;
        if(a>=s.length()||s.charAt(a)!='(') return s.substring(0,i)+s.substring(i+5);
        int endA=matching(s,a); if(endA<0)return s;
        int b=endA+1; if(b>=s.length()||s.charAt(b)!='(')return s.substring(0,i)+s.substring(i+5);
        int endB=matching(s,b); if(endB<0)return s;
        String num=convertFractions(s.substring(a+1,endA)), den=convertFractions(s.substring(b+1,endB));
        return convertFractions(s.substring(0,i)+"(("+num+")/("+den+"))"+s.substring(endB+1));
    }
    static int matching(String s,int open){int d=0;for(int i=open;i<s.length();i++){if(s.charAt(i)=='(')d++;if(s.charAt(i)==')'&&--d==0)return i;}return -1;}

    static final class Parser {
        final String s; int p=0;
        Parser(String input){s=clean(input).toLowerCase(Locale.ROOT);}
        Node parse(){Node n=expr();if(p<s.length())throw error("Лишний символ «"+s.charAt(p)+"»");return n;}
        Node expr(){Node n=term();while(p<s.length()&&(s.charAt(p)=='+'||s.charAt(p)=='-')){char op=s.charAt(p++);n=new Bin(op,n,term());}return n;}
        Node term(){Node n=unary();while(p<s.length()){
            char c=s.charAt(p);if(c=='*'||c=='/'){p++;n=new Bin(c,n,unary());}
            else if(Character.isLetterOrDigit(c)||c=='('||c=='.')n=new Bin('*',n,unary());else break;
        }return n;}
        Node unary(){if(take('+'))return unary();if(take('-'))return new Unary('-',unary());return power();}
        Node power(){Node n=atom();if(take('^'))n=new Bin('^',n,unary());return n;}
        Node atom(){if(take('(')){Node n=expr();if(!take(')'))throw error("Не закрыта скобка");return n;}
            if(p>=s.length())throw error("Ожидалось число, x или скобка");char c=s.charAt(p);
            if(Character.isDigit(c)||c=='.')return number();if(Character.isLetter(c))return name();throw error("Неожиданный символ");}
        Node number(){int start=p;while(p<s.length()&&(Character.isDigit(s.charAt(p))||s.charAt(p)=='.'))p++;
            try{return new Num(Double.parseDouble(s.substring(start,p)));}catch(Exception e){throw error("Неверное число");}}
        Node name(){int start=p;while(p<s.length()&&Character.isLetter(s.charAt(p)))p++;String id=s.substring(start,p);
            if(id.equals("x"))return new X();if(id.equals("pi")||id.equals("p"))return new Num(Math.PI);if(id.equals("e"))return new Num(Math.E);
            if(id.length()==1)return new Sym(id);
            if(List.of("sin","cos","tan","sqrt","ln","log","exp","abs").contains(id)){
                Node arg;if(take('(')){arg=expr();if(!take(')'))throw error("Не закрыта скобка у "+id);}else arg=unary();return new Fn(id,arg);
            }throw error("Неизвестная функция или переменная: "+id+". Используй x, sin, cos, tan, sqrt, ln, log, exp, abs.");}
        boolean take(char c){if(p<s.length()&&s.charAt(p)==c){p++;return true;}return false;}
        IllegalArgumentException error(String m){return new IllegalArgumentException(m+" (позиция "+(p+1)+")");}
    }
    record Rel(String op,int at,int size){}
    static Rel relation(String s){Rel found=null;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c!='<'&&c!='>'&&c!='=')continue;
        int n=(i+1<s.length()&&s.charAt(i+1)=='=')?2:1;String op=s.substring(i,i+n);if(found!=null)throw new IllegalArgumentException("Введи только одно сравнение.");if(op.equals("=="))op="=";found=new Rel(op,i,n);i+=n-1;}return found;}

    static double[] trim(double[] a){int n=a.length;while(n>1&&Math.abs(a[n-1])<EPS)n--;return java.util.Arrays.copyOf(a,n);}
    static double[] add(double[]a,double[]b,double sign){double[]r=new double[Math.max(a.length,b.length)];for(int i=0;i<r.length;i++)r[i]=(i<a.length?a[i]:0)+sign*(i<b.length?b[i]:0);return trim(r);}
    static double[] mul(double[]a,double[]b){double[]r=new double[a.length+b.length-1];for(int i=0;i<a.length;i++)for(int j=0;j<b.length;j++)r[i+j]+=a[i]*b[j];return trim(r);}
    static double[] pow(double[]a,int n){double[]r={1};for(int i=0;i<n;i++)r=mul(r,a);return r;}
    static double[] poly(Node n){
        if(n instanceof Num c)return new double[]{c.value()};if(n instanceof X)return new double[]{0,1};
        if(n instanceof Unary u){double[]a=poly(u.n());if(a==null)return null;double[]r=a.clone();if(u.op()=='-')for(int i=0;i<r.length;i++)r[i]=-r[i];return r;}
        if(n instanceof Fn f){if(!f.arg().hasX()){double v=n.at(0);return Double.isFinite(v)?new double[]{v}:null;}return null;}
        Bin b=(Bin)n;double[]a=poly(b.a()),c=poly(b.b());if(a==null||c==null)return null;
        return switch(b.op()){case '+'->add(a,c,1);case '-'->add(a,c,-1);case '*'->mul(a,c);case '/'->c.length==1&&Math.abs(c[0])>EPS?scale(a,1/c[0]):null;default->c.length==1&&c[0]>=0&&c[0]<=12&&c[0]==Math.rint(c[0])?pow(a,(int)c[0]):null;};
    }
    record Rat(double[] n,double[] d){}
    static Rat rat(Node x){
        if(x instanceof Num n)return new Rat(new double[]{n.value()},new double[]{1});if(x instanceof X)return new Rat(new double[]{0,1},new double[]{1});
        if(x instanceof Unary u){Rat a=rat(u.n());return a==null?null:new Rat(u.op()=='-'?scale(a.n(),-1):a.n(),a.d());}
        if(x instanceof Fn f){if(!f.arg().hasX()){double v=x.at(0);return Double.isFinite(v)?new Rat(new double[]{v},new double[]{1}):null;}return null;}
        Bin b=(Bin)x;Rat a=rat(b.a()),c=rat(b.b());if(a==null||c==null)return null;
        return switch(b.op()){case '+'->new Rat(add(mul(a.n(),c.d()),mul(c.n(),a.d()),1),mul(a.d(),c.d()));
            case '-'->new Rat(add(mul(a.n(),c.d()),mul(c.n(),a.d()),-1),mul(a.d(),c.d()));
            case '*'->new Rat(mul(a.n(),c.n()),mul(a.d(),c.d()));case '/'->new Rat(mul(a.n(),c.d()),mul(a.d(),c.n()));
            default->c.n().length==1&&c.d().length==1&&Math.abs(c.d()[0])>EPS&&c.n()[0]>=0&&c.n()[0]<=12&&c.n()[0]==Math.rint(c.n()[0])?
                    new Rat(pow(a.n(),(int)c.n()[0]),pow(a.d(),(int)c.n()[0])):null;};
    }
    static double[] scale(double[]a,double k){double[]r=a.clone();for(int i=0;i<r.length;i++)r[i]*=k;return trim(r);}
    static java.util.Set<String> variables(Node n){java.util.Set<String>v=new java.util.LinkedHashSet<>();collectVariables(n,v);return v;}
    static void collectVariables(Node n,java.util.Set<String>v){if(n instanceof X){v.add("x");return;}if(n instanceof Sym s){v.add(s.name());return;}if(n instanceof Unary u){collectVariables(u.n(),v);return;}if(n instanceof Fn f){collectVariables(f.arg(),v);return;}if(n instanceof Bin b){collectVariables(b.a(),v);collectVariables(b.b(),v);}}
    static Node sn(double x){return new Num(x);}
    static boolean zero(Node n){return n instanceof Num x&&Math.abs(x.value())<EPS;}
    static Node sadd(Node a,Node b){if(zero(a))return b;if(zero(b))return a;if(a instanceof Num x&&b instanceof Num y)return sn(x.value()+y.value());return new Bin('+',a,b);}
    static Node sneg(Node a){if(a instanceof Num x)return sn(-x.value());if(a instanceof Unary u&&u.op()=='-')return u.n();if(a instanceof Bin b&&b.op()=='+')return ssub(sneg(b.a()),b.b());if(a instanceof Bin b&&b.op()=='-')return ssub(b.b(),b.a());if(a instanceof Bin b&&b.op()=='/')return sdiv(sneg(b.a()),b.b());return new Unary('-',a);}
    static Node ssub(Node a,Node b){if(zero(b))return a;if(zero(a))return sneg(b);if(b instanceof Unary u&&u.op()=='-')return sadd(a,u.n());if(a instanceof Num x&&b instanceof Num y)return sn(x.value()-y.value());return new Bin('-',a,b);}
    static Node smul(Node a,Node b){if(zero(a)||zero(b))return sn(0);if(a instanceof Num x&&x.value()<0)return sneg(smul(sn(-x.value()),b));if(b instanceof Num y&&y.value()<0)return sneg(smul(a,sn(-y.value())));if(a instanceof Num x&&Math.abs(x.value()-1)<EPS)return b;if(b instanceof Num x&&Math.abs(x.value()-1)<EPS)return a;if(a instanceof Num x&&b instanceof Num y)return sn(x.value()*y.value());return new Bin('*',a,b);}
    static Node sdiv(Node a,Node b){if(b instanceof Num y&&y.value()<0)return sneg(sdiv(a,sn(-y.value())));if(a instanceof Num x&&b instanceof Num y&&Math.abs(y.value())>EPS)return sn(x.value()/y.value());if(b instanceof Num y&&Math.abs(y.value()-1)<EPS)return a;return new Bin('/',a,b);}
    static List<Node> symbolicPoly(Node n,String target){
        if(n instanceof Num)return new ArrayList<>(List.of(n));
        if(n instanceof X)return target.equals("x")?new ArrayList<>(List.of(sn(0),sn(1))):new ArrayList<>(List.of(n));
        if(n instanceof Sym s)return target.equals(s.name())?new ArrayList<>(List.of(sn(0),sn(1))):new ArrayList<>(List.of(n));
        if(n instanceof Unary u){List<Node>a=symbolicPoly(u.n(),target);if(a==null)return null;if(u.op()=='-')for(int i=0;i<a.size();i++)a.set(i,sneg(a.get(i)));return trimSym(a);}
        if(n instanceof Fn f)return variables(f.arg()).contains(target)?null:new ArrayList<>(List.of(n));
        Bin b=(Bin)n;List<Node>a=symbolicPoly(b.a(),target),c=symbolicPoly(b.b(),target);if(a==null||c==null)return null;
        if(b.op()=='+'||b.op()=='-'){List<Node>r=new ArrayList<>();for(int i=0;i<Math.max(a.size(),c.size());i++){Node l=i<a.size()?a.get(i):sn(0),q=i<c.size()?c.get(i):sn(0);r.add(b.op()=='+'?sadd(l,q):ssub(l,q));}return trimSym(r);}
        if(b.op()=='*'){List<Node>r=new ArrayList<>();for(int i=0;i<a.size()+c.size()-1;i++)r.add(sn(0));for(int i=0;i<a.size();i++)for(int j=0;j<c.size();j++)r.set(i+j,sadd(r.get(i+j),smul(a.get(i),c.get(j))));return trimSym(r);}
        if(b.op()=='/'&&c.size()==1){List<Node>r=new ArrayList<>();for(Node x:a)r.add(sdiv(x,c.get(0)));return trimSym(r);}
        if(b.op()=='^'&&c.size()==1&&c.get(0) instanceof Num e&&e.value()>=0&&e.value()<=6&&e.value()==Math.rint(e.value())){List<Node>r=new ArrayList<>(List.of(sn(1)));for(int k=0;k<(int)e.value();k++){List<Node>next=new ArrayList<>();for(int i=0;i<r.size()+a.size()-1;i++)next.add(sn(0));for(int i=0;i<r.size();i++)for(int j=0;j<a.size();j++)next.set(i+j,sadd(next.get(i+j),smul(r.get(i),a.get(j))));r=trimSym(next);}return r;}
        return null;
    }
    static List<Node> trimSym(List<Node>a){while(a.size()>1&&zero(a.get(a.size()-1)))a.remove(a.size()-1);return a;}
    static String symText(Node n){if(n instanceof Num x)return fmt(x.value());if(n instanceof X)return "x";if(n instanceof Sym s)return s.name();if(n instanceof Unary u)return u.op()+"("+symText(u.n())+")";if(n instanceof Fn f)return f.name()+"("+symText(f.arg())+")";Bin b=(Bin)n;return "("+symText(b.a())+" "+b.op()+" "+symText(b.b())+")";}
    static void solveSymbolic(Node f,java.util.Set<String>vars,String op,PrintStream out){
        if(!op.equals("=")){out.println("В этом неравенстве несколько переменных. Подставь значения всех переменных, кроме одной.");return;}
        if(vars.size()==1){String v=vars.iterator().next();List<Node>c=symbolicPoly(f,v);if(c!=null&&c.stream().allMatch(x->x instanceof Num)){double[]p=new double[c.size()];for(int i=0;i<p.length;i++)p[i]=((Num)c.get(i)).value();List<Double>rs=roots(p);out.println("Уравнение с переменной "+v+":");out.println(rs.isEmpty()?"Действительных решений нет.":rs.stream().map(x->v+" = "+fmt(x)).reduce((a,b)->a+"; "+b).orElse(""));return;}}
        out.println("В уравнении несколько переменных, поэтому одного числового ответа нет.");
        out.println("Выражаем каждую переменную через остальные:");
        for(String v:vars){List<Node>c=symbolicPoly(f,v);if(c==null){out.println(v+": для этой формы пока не удалось преобразовать выражение.");continue;}c=trimSym(c);int degree=c.size()-1;
            if(degree==0){out.println(v+": уравнение от неё не зависит.");continue;}
            if(degree==1){if(zero(c.get(1))){out.println(v+": решений нет.");continue;}out.println(v+" = "+symText(sdiv(sneg(c.get(0)),c.get(1))));}
            else if(degree==2){Node A=c.get(2),B=c.get(1),C=c.get(0);if(zero(B))out.println(v+" = ±sqrt("+symText(sdiv(sneg(C),A))+")");else{Node disc=ssub(smul(B,B),smul(sn(4),smul(A,C)));Node den=smul(sn(2),A);out.println(v+" = (−"+symText(B)+" ± sqrt("+symText(disc)+")) / "+symText(den));}}
            else out.println(v+": степень выше 2 — символическая формула пока не поддерживается.");
        }
    }
    static double eval(double[]c,double x){double v=0;for(int i=c.length-1;i>=0;i--)v=v*x+c[i];return v;}
    static String fmt(double x){if(Math.abs(x)<1e-10)x=0;if(Math.abs(x-Math.rint(x))<1e-9&&Math.abs(x)<1e12)return String.format(Locale.US,"%.0f",x);return String.format(Locale.US,"%.8g",x);}
    static List<Double> roots(double[]c){c=trim(c);List<Double>r=new ArrayList<>();int d=c.length-1;
        if(d==0)return r;if(d==1){r.add(-c[0]/c[1]);return r;}
        if(d==2){double a=c[2],b=c[1],z=c[0],disc=b*b-4*a*z;if(disc<-EPS)return r;if(disc<0)disc=0;double q=Math.sqrt(disc);r.add((-b-q)/(2*a));if(q>EPS)r.add((-b+q)/(2*a));r.sort(Double::compare);return r;}
        double step=2*RANGE/GRID,px=-RANGE,pv=eval(c,px);
        for(int i=1;i<=GRID;i++){double x=-RANGE+i*step,v=eval(c,x);if(Double.isFinite(pv)&&Double.isFinite(v)&&pv*v<0){double lo=px,hi=x,fl=pv;for(int k=0;k<80;k++){double m=(lo+hi)/2,fm=eval(c,m);if(fl*fm<=0)hi=m;else{lo=m;fl=fm;}}double root=(lo+hi)/2;if(r.stream().noneMatch(t->Math.abs(t-root)<1e-5))r.add(root);}px=x;pv=v;}
        return r;
    }
    static String polyText(double[]c){StringBuilder s=new StringBuilder();for(int i=c.length-1;i>=0;i--){double v=c[i];if(Math.abs(v)<EPS)continue;if(s.length()>0)s.append(v<0?" - ":" + ");else if(v<0)s.append('-');double a=Math.abs(v);if(i==0||Math.abs(a-1)>EPS)s.append(fmt(a));if(i>0){s.append('x');if(i>1)s.append('^').append(i);}}return s.length()==0?"0":s.toString();}
    static boolean satisfies(double y,String op){return switch(op){case "<"->y<0;case "<="->y<=0;case ">"->y>0;case ">="->y>=0;default->false;};}
    static String interval(double l,double r,boolean lc,boolean rc){return (lc?"[":"(")+(Double.isInfinite(l)?"−∞":fmt(l))+"; "+(Double.isInfinite(r)?"+∞":fmt(r))+(rc?"]":")");}

    static String solve(String input){ByteArrayOutputStream buf=new ByteArrayOutputStream();PrintStream out=new PrintStream(buf,true,StandardCharsets.UTF_8);try{solveInto(input,out);}catch(Exception e){out.println("Не удалось разобрать задачу: "+e.getMessage());}return buf.toString(StandardCharsets.UTF_8).trim();}
    static void solveInto(String raw,PrintStream out){String s=clean(raw);Rel rel=relation(s);String left=rel==null?s:s.substring(0,rel.at()),right=rel==null?"0":s.substring(rel.at()+rel.size());Node a=new Parser(left).parse(),b=new Parser(right).parse();
        if(rel==null&&!a.hasX()){double v=a.at(0);if(!Double.isFinite(v))throw new IllegalArgumentException("Выражение не определено.");out.println("Вычисление: "+fmt(v));return;}
        Node f=new Bin('-',a,b);String op=rel==null?"=":rel.op();java.util.Set<String> vars=variables(f);if(vars.size()>1||vars.stream().anyMatch(v->!v.equals("x"))){solveSymbolic(f,vars,op,out);return;}Rat r=rat(f);
        if(op.equals("=")){if(r!=null){List<Double>rs=roots(r.n());rs.removeIf(x->!Double.isFinite(x)||Math.abs(eval(r.d(),x))<1e-7);out.println("Уравнение: "+left+" = "+right);if(r.n().length==1){out.println(Math.abs(r.n()[0])<EPS?"Равенство верно при всех допустимых x.":"Решений нет.");return;}
                if(r.n().length==2){out.println("Шаг 1. Переносим всё в левую часть: "+polyText(r.n())+" = 0");out.println("Шаг 2. Линейное уравнение: x = −b/a = "+fmt(-r.n()[0]/r.n()[1]));}
                else if(r.n().length==3){double A=r.n()[2],B=r.n()[1],C=r.n()[0],D=B*B-4*A*C;out.println("Шаг 1. Приводим к виду ax² + bx + c = 0: "+polyText(r.n())+" = 0");out.println("Шаг 2. Дискриминант D = b² − 4ac = "+fmt(D));}
                out.println(rs.isEmpty()?"Действительных корней не найдено.":"Ответ: "+joinRoots(rs));return;}
            out.println("Использую численный поиск корней на интервале [−1000; 1000].");List<Double>rs=findNumeric(f);out.println(rs.isEmpty()?"Корни на выбранном интервале не найдены.":"Приближённые корни: "+joinRoots(rs));return;}
        if(!List.of("<",">","<=",">=").contains(op))throw new IllegalArgumentException("Поддерживаются =, <, ≤, >, ≥.");
        if(r==null)throw new IllegalArgumentException("Для неравенств пока нужны алгебраические выражения: многочлены и дроби с x.");
        solveInequality(r,op,out);
    }
    static String joinRoots(List<Double>rs){return rs.stream().map(x->"x = "+fmt(x)).reduce((a,b)->a+"; "+b).orElse("");}
    static List<Double> findNumeric(Node f){List<Double>r=new ArrayList<>();double step=2*RANGE/GRID,px=-RANGE,pv=f.at(px);for(int i=1;i<=GRID;i++){double x=-RANGE+i*step,v=f.at(x);if(Double.isFinite(pv)&&Double.isFinite(v)&&pv*v<0){double lo=px,hi=x,fl=pv;for(int k=0;k<70;k++){double m=(lo+hi)/2,fm=f.at(m);if(!Double.isFinite(fm))break;if(fl*fm<=0)hi=m;else{lo=m;fl=fm;}}double z=(lo+hi)/2;if(r.stream().noneMatch(t->Math.abs(t-z)<1e-5))r.add(z);}px=x;pv=v;}return r;}
    record Part(double l,double r,boolean lc,boolean rc){}
    static void solveInequality(Rat rat,String op,PrintStream out){List<Double>n=roots(rat.n()),d=roots(rat.d());List<Double>cuts=new ArrayList<>();for(double x:n)if(Double.isFinite(x)&&Math.abs(x)<RANGE)cuts.add(x);for(double x:d)if(Double.isFinite(x)&&Math.abs(x)<RANGE)cuts.add(x);cuts.sort(Double::compare);List<Double>uniq=new ArrayList<>();for(double x:cuts)if(uniq.isEmpty()||Math.abs(uniq.get(uniq.size()-1)-x)>1e-6)uniq.add(x);
        out.println("Шаг 1. Критические точки числителя: "+(n.isEmpty()?"нет":n.stream().map(EquationSolver::fmt).reduce((a,b)->a+", "+b).orElse("")));
        if(!d.isEmpty())out.println("Шаг 2. Запрещённые точки знаменателя: "+d.stream().map(EquationSolver::fmt).reduce((a,b)->a+", "+b).orElse(""));
        List<Part>parts=new ArrayList<>();List<Double>bounds=new ArrayList<>();bounds.add(Double.NEGATIVE_INFINITY);bounds.addAll(uniq);bounds.add(Double.POSITIVE_INFINITY);
        for(int i=0;i<bounds.size()-1;i++){double l=bounds.get(i),h=bounds.get(i+1),sample=Double.isInfinite(l)?h-Math.max(1,Math.abs(h)*.1):Double.isInfinite(h)?l+Math.max(1,Math.abs(l)*.1):(l+h)/2;double y=eval(rat.n(),sample)/eval(rat.d(),sample);if(Double.isFinite(y)&&satisfies(y,op)){boolean closed=op.endsWith("=");boolean lc=closed&&!Double.isInfinite(l)&&isIncludedCritical(l,n,d),rc=closed&&!Double.isInfinite(h)&&isIncludedCritical(h,n,d);parts.add(new Part(l,h,lc,rc));}}
        List<Part>merged=new ArrayList<>();for(Part p:parts){if(merged.isEmpty()){merged.add(p);continue;}Part q=merged.get(merged.size()-1);if(p.l()<q.r()-1e-7||(Math.abs(p.l()-q.r())<1e-7&&(q.rc()||p.lc()))){boolean extendsRight=p.r()>q.r()+1e-7;merged.set(merged.size()-1,new Part(q.l(),Math.max(q.r(),p.r()),q.lc(),extendsRight?p.rc():extendsRight==false&&Math.abs(p.r()-q.r())<1e-7?(q.rc()||p.rc()):q.rc()));}else merged.add(p);}
        out.println("Проверяем знак выражения на каждом промежутке.");out.println(merged.isEmpty()?"Ответ: решений нет.":"Ответ: "+merged.stream().map(p->interval(p.l(),p.r(),p.lc(),p.rc())).reduce((a,b)->a+" ∪ "+b).orElse(""));
    }
    static boolean isIncludedCritical(double x,List<Double>n,List<Double>d){boolean zeroN=n.stream().anyMatch(v->Math.abs(v-x)<1e-6),zeroD=d.stream().anyMatch(v->Math.abs(v-x)<1e-6);return zeroN&&!zeroD;}

    static void showWindow(){SwingUtilities.invokeLater(()->{
        try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception ignored){}
        JLabel label=new JLabel("Введи задачу с переменной x — можно вставить из чата:");label.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,15));
        JTextField input=new JTextField();input.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,19));
        JTextArea result=new JTextArea();result.setEditable(false);result.setLineWrap(true);result.setWrapStyleWord(true);result.setFont(new Font(Font.MONOSPACED,Font.PLAIN,15));result.setBorder(new EmptyBorder(10,10,10,10));
        JButton paste=new JButton("Вставить"),solve=new JButton("Решить"),copy=new JButton("Копировать ответ");
        paste.addActionListener(e->{try{String s=(String)Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);if(s!=null)input.replaceSelection(s.replace('\n',' ').replace('\r',' ').trim());input.requestFocusInWindow();}catch(Exception ex){JOptionPane.showMessageDialog(null,"Не удалось прочитать буфер обмена.");}});
        Runnable run=()->{String s=input.getText().trim();result.setText(s.isEmpty()?"Вставь или введи уравнение.":solve(s));result.setCaretPosition(0);};
        solve.addActionListener(e->run.run());input.addActionListener(e->run.run());
        copy.addActionListener(e->{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(result.getText()),null);});
        JPanel buttons=new JPanel(new BorderLayout(6,0));buttons.add(paste,BorderLayout.WEST);buttons.add(solve,BorderLayout.CENTER);buttons.add(copy,BorderLayout.EAST);
        JPanel row=new JPanel(new BorderLayout(8,0));row.add(input,BorderLayout.CENTER);row.add(buttons,BorderLayout.EAST);
        JPanel top=new JPanel(new BorderLayout(0,7));top.add(label,BorderLayout.NORTH);top.add(row,BorderLayout.CENTER);
        JPanel root=new JPanel(new BorderLayout(0,12));root.setBorder(new EmptyBorder(16,16,16,16));root.add(top,BorderLayout.NORTH);root.add(new JScrollPane(result),BorderLayout.CENTER);
        JFrame f=new JFrame("Математический решатель");f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);f.setContentPane(root);f.setPreferredSize(new Dimension(820,560));f.pack();f.setLocationRelativeTo(null);f.setVisible(true);input.requestFocusInWindow();
    });}
    public static void main(String[]args){if(args.length==0){showWindow();return;}String s=String.join(" ",args);System.out.println(solve(s));}
}
