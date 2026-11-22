package org.cdavalidator;

import java.math.BigDecimal;
import java.time.*;
import java.util.regex.*;
import net.sf.saxon.s9api.*;

/** CDA TS comparisons preserve precision and offsets; uncertainty is never a pass. */
final class CdaTimestamp {
    static final String NS = "urn:cdavalidator:timestamp";
    private static final Pattern FORMAT = Pattern.compile("(\\d{4})(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(?:\\.(\\d+))?([+-]\\d{4})?");
    record Value(LocalDateTime local, int precision, String fraction, ZoneOffset offset) {}
    static Value parse(String input) {
        if(input.length()>64)throw new IllegalArgumentException("Timestamp precision exceeds supported bounds");
        var m = FORMAT.matcher(input);
        if (!m.matches() || (m.group(7) != null && m.group(6) == null)) throw new IllegalArgumentException("Invalid CDA timestamp");
        int year=Integer.parseInt(m.group(1));
        if(year==0)throw new IllegalArgumentException("CDA timestamps have no year zero");
        int[] parts={year,1,1,0,0,0}; int precision=1;
        for(int i=2;i<=6;i++) if(m.group(i)!=null){parts[i-1]=Integer.parseInt(m.group(i));precision=i;}
        String fraction=m.group(7); if(fraction!=null)precision=7;
        ZoneOffset offset=m.group(8)==null?null:ZoneOffset.of(m.group(8));
        return new Value(LocalDateTime.of(parts[0],parts[1],parts[2],parts[3],parts[4],parts[5]),precision,fraction,offset);
    }
    static String compare(String left,String right,String operation) {
        try {
            if(!operation.equals("le")&&!operation.equals("gt"))return "unknown";
            var a=parse(left);var b=parse(right);
            if((a.offset==null)!=(b.offset==null))return "unknown";
            int common=Math.min(a.precision,b.precision);
            // Offsets on partial dates cannot establish a single instant.
            if(a.offset!=null && common<4)return "unknown";
            if(a.offset!=null && common==4 && !a.offset.equals(b.offset) &&
                (a.offset.getTotalSeconds()%3600!=0 || b.offset.getTotalSeconds()%3600!=0))return "unknown";
            boolean local=a.offset==null||a.offset.equals(b.offset);
            LocalDateTime av=local?a.local:LocalDateTime.ofInstant(a.local.toInstant(a.offset),ZoneOffset.UTC);
            LocalDateTime bv=local?b.local:LocalDateTime.ofInstant(b.local.toInstant(b.offset),ZoneOffset.UTC);
            int[] ap={av.getYear(),av.getMonthValue(),av.getDayOfMonth(),av.getHour(),av.getMinute(),av.getSecond()};
            int[] bp={bv.getYear(),bv.getMonthValue(),bv.getDayOfMonth(),bv.getHour(),bv.getMinute(),bv.getSecond()};
            int comparison=0;
            for(int i=0;i<Math.min(common,6)&&comparison==0;i++)comparison=Integer.compare(ap[i],bp[i]);
            if(comparison==0 && common==7) comparison=new BigDecimal("0."+a.fraction).compareTo(new BigDecimal("0."+b.fraction));
            if(comparison==0 && a.precision!=b.precision)return "unknown";
            return Boolean.toString(operation.equals("le")?comparison<=0:comparison>0);
        } catch(RuntimeException e){return "unknown";}
    }
    static void register(Processor processor) {
        processor.registerExtensionFunction(new ExtensionFunction(){
            public QName getName(){return new QName("local-ts",NS,"compare");}
            public SequenceType getResultType(){return SequenceType.makeSequenceType(ItemType.STRING,OccurrenceIndicator.ONE);}
            public SequenceType[] getArgumentTypes(){var s=getResultType();return new SequenceType[]{s,s,s};}
            public XdmValue call(XdmValue[] args){return new XdmAtomicValue(compare(args[0].itemAt(0).getStringValue(),args[1].itemAt(0).getStringValue(),args[2].itemAt(0).getStringValue()));}
        });
    }
}
