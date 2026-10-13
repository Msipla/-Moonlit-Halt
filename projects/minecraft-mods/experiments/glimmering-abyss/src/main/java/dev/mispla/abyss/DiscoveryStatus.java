package dev.mispla.abyss;

/** The same honest state text is used by the guide and the creative diagnostics. */
public final class DiscoveryStatus {
    private DiscoveryStatus(){}
    public static String describe(String stage,int cursor,int attempt,String reason,int dreams){
        return switch(stage){
            case "search" -> "微光正在群山中寻找合适的位置。已检查 "+attempt+" 个候选。"+(reason.isBlank()?"":"上一处未通过覆盖或存档保护检查，正在自动重选。");
            case "check" -> "微光正在检查山体覆盖与未探索区域："+cursor+"/"+OverworldSite.CHUNKS+"。请暂时不要前往候选位置。";
            case "carve" -> "地下景观正在生成："+cursor+"/"+OverworldSite.CHUNKS+"。完成后再正常入睡。";
            case "ready" -> dreams==0?"景观已就绪。正常入睡可看见第一段梦境；寻梦微光已可定位入口。":dreams==1?"已记住第一段梦境。另一个夜晚入睡可看见暖光小镇；微光已可定位。":"两段梦境已记住。微光可以反复指引山间站台。";
            case "unavailable" -> "这个存档暂时无法生成景观。"+(reason.startsWith("Requires")?"需要普通主世界和足够的地下高度。":reason.startsWith("No covered")?"有限搜索中未找到覆盖足够、未被使用的山体。":"生成已停止，请保留日志以便排查。")+"继续睡觉不会改变此状态。";
            default -> "景观状态异常，请保留日志。";
        };
    }
}
