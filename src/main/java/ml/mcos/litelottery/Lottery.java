package ml.mcos.litelottery;

import ml.mcos.litelottery.config.Config;
import ml.mcos.litelottery.config.Messages;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Lottery {
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private static final DecimalFormat decimalFormat = new DecimalFormat(",##0.00");
    private YamlConfiguration lotteryInfo;
    private final LiteLottery plugin;
    private final Economy economy;
    public File file;
    public boolean isOK;
    public double prizePool;
    public boolean isRunLottery;
    public boolean runLotteryFinish;
    public List<String> numList = new ArrayList<>();
    private String numbers;
    private boolean notice;
    private String introduction;

    public Lottery(LiteLottery plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        file = new File(plugin.getDataFolder(), getFileName());
        init();
        initNumber();
    }

    public void initNumber() {
        if (numList.size() != 0) {
            numList.clear();
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= Config.maxNumber; i++) {
            String num = getNum(i);
            builder.append(num);
            numList.add(num);
            if (i < Config.maxNumber) {
                builder.append(' ');
            }
        }
        numbers = builder.toString();
    }

    public static String getNum(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    public void init() {
        if (!file.exists()) {
            try {
                if (!file.createNewFile() && !file.createNewFile()) {
                    plugin.getServer().getConsoleSender().sendMessage(Messages.messagePrefix + Messages.cannotCreateNewFile);
                    isOK = false;
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                isOK = false;
            }
        }
        lotteryInfo = YamlConfiguration.loadConfiguration(file);
        if (!lotteryInfo.contains("prizePool")) {
            addPrizePool(Config.initialPrizePool); //新一期奖池余额 = 上期剩余奖池 + 初始奖池
            save();
        } else {
            prizePool = lotteryInfo.getDouble("prizePool");
        }
        isRunLottery = lotteryInfo.getBoolean("isRunLottery");
        runLotteryFinish = isRunLottery;
        notice = false;
        introduction = String.format(Messages.introduction,
                formatDecimal(Config.moneyPerBets),  getLotteryTime(),
                formatDecimal(Config.fifthPrize), formatDecimal(Config.fourthPrize),
                formatDecimal(Config.thirdPrize), formatDecimal(Config.secondPrize),
                formatDecimal(Config.firstPrize), formatDecimal(Config.specialPrize));
        isOK = true;
    }

    private void save() {
        try {
            lotteryInfo.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getFileName() {
        return simpleDateFormat.format(new Date()) + ".yml";
    }

    private void addPrizePool(double value) {
        prizePool += value;
        if (prizePool > Config.maxPrizePool) {
            prizePool = Config.maxPrizePool;
        }
        setPrizePool(prizePool);
    }

    private void subPrizePool(double value) {
        prizePool -= value;
        if (prizePool < 0) {
            prizePool = 0;
        }
        setPrizePool(prizePool);
    }

    private void setPrizePool(double value) {
        prizePool = value;
        setValue("prizePool", prizePool);
    }

    private void setValue(String path, Object value) {
        lotteryInfo.set(path, value);
    }

    public void showLotteryInfo(Player player) {
        sendMessage(player, introduction);
        //sendMessage(player, "§3投注方法: /lt <投注数量> <所选号码>\n例如: /lt 2 07 12 02 10 05 表示购买2注(07 12 02 10 05)");
        sendMessage(player, Messages.placeBetMethod);
        //sendMessage(player, "§6可选号码: §2" + numbers);
        sendMessage(player, Messages.optionalNumbers + numbers);
        //sendMessage(player, "§a当前奖池资金: §e$" + formatDecimal(prizePool));
        sendMessage(player, Messages.currentPrizePool + formatDecimal(prizePool));
        showBets(player);
    }

    private void showBets(Player player) {
        ConfigurationSection cs = lotteryInfo.getConfigurationSection("bets." + player.getName());
        if (cs == null) {
            return;
        }
        Set<String> bets = cs.getKeys(false);
        if (bets.isEmpty()) {
            return;
        }
        //StringBuilder builder = new StringBuilder("§d本期投注 §3(您): \n");
        StringBuilder builder = new StringBuilder(Messages.s1 + "\n");
        int amount = 0;
        for (String bet : bets) {
            int i = cs.getInt(bet + ".amount");
            amount += i;
            builder.append("§7 >> §b").append(bet).append(" §a共§6").append(i).append("§a注 ").append(cs.getString(bet + ".state")).append('\n');
        }
        sendMessage(player, builder.substring(0, builder.length() - 1));
        //sendMessage(player, "§3本期投注统计: §a" + bets.size() + "§3组号码 §6" + amount + "§3注");
        sendMessage(player, Messages.getMessage(Messages.s2, String.valueOf(bets.size()), String.valueOf(amount)));
    }

    private String getLotteryTime() {
        return getNum(Config.lotteryHour) + ":" + getNum(Config.lotteryMinute);
    }

    private static String formatDecimal(double d) {
        return decimalFormat.format(d);
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(Messages.messagePrefix + message);
    }

    public void placeBet(Player player, String[] args) {
        if (isRunLottery) {
            //sendMessage(player, "§c今天已经开奖, 明天再来投注吧。");
            sendMessage(player, Messages.s3);
        } else if (args.length < 2) {
            //sendMessage(player, "§6用法: §7/lt <投注数量> <所选号码>");
            sendMessage(player, Messages.s4);
        } else if (args.length > 6) {
            //sendMessage(player, "§6你选的号码太多了, 最多只能选5个号码。");
            sendMessage(player, Messages.s5);
        } else {
            int amount = parseInt(args[0]);
            if (amount < 1) {
                //sendMessage(player, "§6无效的投注数量: §7" + args[0]);
                sendMessage(player, Messages.s6 + args[0]);
                return;
            }
            if (args.length == 2 && args[1].equals("random")) {
                String playerName = player.getName();
                if (getRandomCount(playerName) >= Config.randomMax) {
                    //sendMessage(player, "§c你今天使用随机选号的次数已达上限。");
                    sendMessage(player, Messages.s7);
                } else if (player.hasPermission("LiteLottery.bypass") || checkRandomInterval(playerName)) {
                    if (buyLottery(player, String.join(" ", randomNumber()), amount)) {
                        addRandomCount(playerName);
                        setRandomTime(playerName, System.currentTimeMillis());
                        save();
                    }
                } else {
                    //sendMessage(player, "§b你使用的太快了，喝口茶休息一会再来吧。");
                    sendMessage(player, Messages.s8);
                }
                return;
            }
            String nums = mergeArgs(args);
            if (checkNum(player, nums)) {
                buyLottery(player, nums, amount);
            }
        }
    }

    private boolean checkNum(Player player, String s) {
        String[] nums = s.split(" ");
        ArrayList<String> temp = new ArrayList<>();
        for (String num : nums) {
            if (temp.contains(num)) {
                //sendMessage(player, "§6错误, 选择的号码中含有重复号码: §7" + num);
                sendMessage(player, Messages.s9 + num);
                return false;
            } else if (!numList.contains(num)) {
                //sendMessage(player, "§6错误, 选择的号码不在可选号码内: §7" + num);
                sendMessage(player, Messages.s10 + num);
                return false;
            }
            temp.add(num);
        }
        return true;
    }

    private static String mergeArgs(String[] args) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i != 1) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private boolean buyLottery(Player player, String nums, int amount) {
        if (Config.maxNums != 0 && getBetNums(player.getName()) == Config.maxNums && getBetAmount(player.getName(), nums) == 0) {
            //player.sendMessage("§c你本期投注的号码数量已达上限。");
            sendMessage(player, Messages.s11);
        } else if (Config.maxBets != 0 && getBetsAmount(player.getName()) + amount > Config.maxBets) {
            //player.sendMessage("§c错误, 投注数量超过了本期投注上限。");
            sendMessage(player, Messages.s12);
        } else {
            double money = amount * Config.moneyPerBets;
            if (economy.has(player, money)) {
                economy.withdrawPlayer(player, money);
                addPrizePool(money);
                //有些服主用命令控制一天多次开奖 所以要防止玩家钻空子把已开奖的号码再投一注变为等待开奖
                if (!getBetState(player.getName(), nums).equals(Messages.s13)) {
                    setBetAmount(player.getName(), nums, amount);
                } else {
                    addBetAmount(player.getName(), nums, amount);
                }
                //setBetState(player.getName(), nums, "§7(等待开奖)");
                setBetState(player.getName(), nums, Messages.s13);
                save();
                //sendMessage(player, "§a§l购买§6" + amount + "§a§l注§3(" + nums + ") §a§l共花费: §b$" + formatDecimal(money));
                sendMessage(player, Messages.getMessage(Messages.s14, String.valueOf(amount), nums) + formatDecimal(money));
                playSound(player);
                return true;
            } else {
                //player.sendMessage("§c错误: §7你没有足够的金钱。");
                sendMessage(player, Messages.s15);
                //player.sendMessage("§7共计需要: $" + formatDecimal(money) + " (每注价格: $" + formatDecimal(Config.moneyPerBets) + ")");
                sendMessage(player, Messages.getMessage(Messages.s16, formatDecimal(money), formatDecimal(Config.moneyPerBets)));
            }
        }
        return false;
    }

    private void playSound(Player player) {
        if (plugin.mcVersion < 9) {
            playSound(player, Sound.valueOf("ORB_PICKUP"));
        } else {
            playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        }
    }

    private void playSound(Player player, String sound) {
        if (plugin.mcVersion < 9) {
            switch (sound) {
                case "ENTITY_EXPERIENCE_ORB_PICKUP":
                    sound = "ORB_PICKUP";
                    break;
                case "ENTITY_PLAYER_LEVELUP":
                    sound = "LEVEL_UP";
                    break;
                case "ENTITY_ENDER_DRAGON_DEATH":
                    sound = "ENDERDRAGON_DEATH";
            }
        }
        playSound(player, Sound.valueOf(sound));
    }

    private void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
    }

    private void playSoundAll(String sound) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            playSound(player, sound);
        }
    }

    private String getBetState(String player, String bet) {
        return lotteryInfo.getString("bets." + player + "." + bet + ".state", Messages.s13);
    }

    private void setBetState(String player, String bet, String state) {
        setValue("bets." + player + "." + bet + ".state", state);
    }

    private void addBetAmount(String player, String bet, int amount) {
        setBetAmount(player, bet, getBetAmount(player, bet) + amount);
    }

    private void setBetAmount(String player, String bet, int amount) {
        setValue("bets." + player + "." + bet + ".amount", amount);
    }

    private int getBetAmount(String player, String bet) {
        return lotteryInfo.getInt("bets." + player + "." + bet + ".amount");
    }

    private int getBetsAmount(String player) {
        ConfigurationSection cs = lotteryInfo.getConfigurationSection("bets." + player);
        if (cs == null) {
            return 0;
        }
        Set<String> bets = cs.getKeys(false);
        if (bets.isEmpty()) {
            return 0;
        }
        int amount = 0;
        for (String bet : bets) {
            int i = cs.getInt(bet + ".amount");
            amount += i;
        }
        return amount;
    }

    private int getBetNums(String player) {
        ConfigurationSection cs = lotteryInfo.getConfigurationSection("bets." + player);
        return cs == null ? 0 : cs.getKeys(false).size();
    }

    private static List<String> randomNumber() {
        Random random = new Random();
        ArrayList<String> list = new ArrayList<>();
        while (list.size() < 5) {
            String num = getNum(random.nextInt(Config.maxNumber) + 1);
            if (list.contains(num)) {
                continue;
            }
            list.add(num);
        }
        return list;
    }

    private static void randomNumber(ArrayList<String> list) {
        Random random = new Random();
        while (list.size() < 5) {
            String num = getNum(random.nextInt(Config.maxNumber) + 1);
            if (!list.contains(num)) {
                list.add(num);
                return;
            }
        }
    }

    private boolean checkRandomInterval(String player) {
        long diff = System.currentTimeMillis() - getRandomTime(player);
        return diff >= Config.randomInterval * 1000L;
    }

    private void setRandomTime(String player, long time) {
        setValue("random." + player + ".time", time);
    }

    private long getRandomTime(String player) {
        return lotteryInfo.getLong("random." + player + ".time");
    }

    private void addRandomCount(String player) {
        setRandomCount(player, getRandomCount(player) + 1);
    }

    private void setRandomCount(String player, int count) {
        setValue("random." + player + ".count", count);
    }

    private int getRandomCount(String player) {
        return lotteryInfo.getInt("random." + player + ".count");
    }

    /**
     * 将字符串解析为10进制整数
     * @param num 十进制整数字符串
     * @return 如果结果小于1 返回-1
     */
    private static int parseInt(String num) {
        try {
            int i = Integer.parseInt(num);
            return i < 1 ? -1 : i;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public boolean checkTime(Calendar cal) {
        int week = cal.get(Calendar.DAY_OF_WEEK);
        if (!Config.lotteryWeekDays.contains(week)) return false;

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        if (hour == Config.lotteryHour && minute == Config.lotteryMinute) {
            return true;
        } else if (Config.notice && !notice) {
            minute += 10;
            if (minute > 59) {
                hour++; //hour超过23也无所谓 设计就是如果开奖时间在00:10之前则不提示开奖
                minute = minute % 60;
            }
            if (hour == Config.lotteryHour && minute == Config.lotteryMinute) {
                //broadcastMessage("§b开奖将在十分钟后进行, 不要走开, 也许你就是本期的特等奖得主!");
                broadcastMessage(Messages.s17);
                notice = true;
            }
        }
        return false;
    }

    private void broadcastMessage(String message) {
        plugin.getServer().broadcastMessage(Messages.messagePrefix + message);
    }

    public void tryRunLottery() {
        if (!isRunLottery) {
            isRunLottery = true;
            setValue("isRunLottery", true);
            runLottery();
        }
    }

    private void runLottery() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ArrayList<String> prizeNum = new ArrayList<>();
            //broadcastMessage("§6§l正在开奖§6···");
            broadcastMessage(Messages.s18);
            //sendTitleAll("§6正在开奖···", "§k00 00 00 00 00", 5);
            sendTitleAll(Messages.s19, "§k00 00 00 00 00", 5);
            playSoundAll("ENTITY_EXPERIENCE_ORB_PICKUP");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 1; i < 5; i++) {
                randomNumber(prizeNum);
                //broadcastMessage("§b第" + i + "个中奖号码是: §c" + prizeNum.get(i - 1));
                broadcastMessage(Messages.getMessage(Messages.s20, String.valueOf(i)) + prizeNum.get(i - 1));
                if (i != 1) {
                    builder.append(' ');
                }
                builder.append(prizeNum.get(i - 1));
                //sendTitleAll("§6正在开奖···", "§c" + builder + " §r§k" + repeat00(5 - i), 5);
                sendTitleAll(Messages.s19, "§c" + builder + " §r§k" + repeat00(5 - i), 5);
                playSoundAll("ENTITY_EXPERIENCE_ORB_PICKUP");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            randomNumber(prizeNum);
            //broadcastMessage("§b第5个中奖号码是: §c" + prizeNum.get(4));
            broadcastMessage(Messages.getMessage(Messages.s20, "5") + prizeNum.get(4));
            builder.append(' ').append(prizeNum.get(4));
            //sendTitleAll("§6本期中奖号码", "§a" + builder, 7);
            sendTitleAll(Messages.s21, "§a" + builder, 7);
            //broadcastMessage("§6本期中奖号码: §a" + builder);
            broadcastMessage(Messages.s22 + builder);
            setValue("prizeNumber", builder.toString());
            HashMap<String, Integer> firstPrize = new HashMap<>();
            HashMap<String, Integer> secondPrize = new HashMap<>();
            HashMap<String, Integer> thirdPrize = new HashMap<>();
            HashMap<String, Integer> fifthPrize = new HashMap<>();
            HashMap<String, Integer> fourthPrize = new HashMap<>();
            ArrayList<String> players = getPlayers();
            for (String player : players) {
                ArrayList<String> bets = getBets(player);
                for (String bet : bets) {
                    if (!getBetState(player, bet).equals(Messages.s13)) {
                        continue; //已开过奖的号码不再开奖
                    }
                    if (bet.equals(builder.toString())) {
                        //setBetState(player, bet, "§d§l(特等奖)");
                        setBetState(player, bet, Messages.s23);
                        int amount = getBetAmount(player, bet);
                        //String msg = "§4§l难以置信！ §a" + player + "§c§l抽中§d§l特等奖§6§l" + amount + "§c§l注！";
                        String msg = Messages.getMessage(Messages.s24, player, String.valueOf(amount));
                        broadcastMessage(msg);
                        broadcastMessage(msg);
                        broadcastMessage(msg);
                        playSoundAll("ENTITY_ENDER_DRAGON_DEATH");
                        Player p = plugin.getServer().getPlayerExact(player);
                        if (p == null) {
                            //broadcastMessage("§6很遗憾··· 由于特等奖得主当前并未在线, 只能获得20%的奖金。");
                            broadcastMessage(Messages.s25);
                            economy.depositPlayer(getOfflinePlayer(player), Math.min(Config.specialPrize * amount * 0.2, Config.specialPrizeMax));
                        } else {
                            double money = Math.min(Config.specialPrize * amount, Config.specialPrizeMax);
                            economy.depositPlayer(p, money);
                            //sendMessage(p, "§6你抽中§d§l特等奖§c, 获得了: §b$" + formatDecimal(money));
                            sendMessage(p, Messages.s26 + formatDecimal(money));
                        }
                    } else {
                        String[] nums = bet.split(" ");
                        int count = 0;
                        for (String num : nums) {
                            if (prizeNum.contains(num)) {
                                count++;
                            }
                        }
                        if (count != 0) {
                            int amount = getBetAmount(player, bet);
                            if (count == 5) {
                                //setBetState(player, bet, "§c(一等奖)");
                                setBetState(player, bet, Messages.s27);
                                firstPrize.merge(player, amount, Integer::sum);
                            } else if (count == 4) {
                                //setBetState(player, bet, "§b(二等奖)");
                                setBetState(player, bet, Messages.s28);
                                secondPrize.merge(player, amount, Integer::sum);
                            } else if (count == 3) {
                                //setBetState(player, bet, "§a(三等奖)");
                                setBetState(player, bet, Messages.s29);
                                thirdPrize.merge(player, amount, Integer::sum);
                            } else if (count == 2) {
                                //setBetState(player, bet, "§3(四等奖)");
                                setBetState(player, bet, Messages.s30);
                                fifthPrize.merge(player, amount, Integer::sum);
                            } else if (count == 1) {
                                //setBetState(player, bet, "§9(五等奖)");
                                setBetState(player, bet, Messages.s31);
                                fourthPrize.merge(player, amount, Integer::sum);
                            }
                        } else {
                            //setBetState(player, bet, "§7(未中奖)");
                            setBetState(player, bet, Messages.s32);
                        }
                    }
                }
            }
            String firstPrizeResult;
            String secondPrizeResult;
            String thirdPrizeResult;
            String fifthPrizeResult;
            String fourthPrizeResult;
            if (fourthPrize.isEmpty()) {
                //fourthPrizeResult = "§9五等奖: §7无";
                fourthPrizeResult = Messages.s33;
            } else {
                //fourthPrizeResult = "§9五等奖: " + getNamesAndGiveMoney(fourthPrize, Config.fifthPrize, Config.fifthPrizeMax, "§9五等奖");
                fourthPrizeResult = Messages.s34 + getNamesAndGiveMoney(fourthPrize, Config.fifthPrize, Config.fifthPrizeMax, Messages.s43);
            }
            if (fifthPrize.isEmpty()) {
                //fifthPrizeResult = "§3四等奖: §7无";
                fifthPrizeResult = Messages.s35;
            } else {
                //fifthPrizeResult = "§3四等奖: " + getNamesAndGiveMoney(fifthPrize, Config.fourthPrize, Config.fourthPrizeMax, "§3四等奖");
                fifthPrizeResult = Messages.s36 + getNamesAndGiveMoney(fifthPrize, Config.fourthPrize, Config.fourthPrizeMax, Messages.s44);
            }
            if (thirdPrize.isEmpty()) {
                //thirdPrizeResult = "§a三等奖: §7无";
                thirdPrizeResult = Messages.s37;
            } else {
                //thirdPrizeResult = "§a三等奖: " + getNamesAndGiveMoney(thirdPrize, Config.thirdPrize, Config.thirdPrizeMax, "§d三等奖");
                thirdPrizeResult = Messages.s38 + getNamesAndGiveMoney(thirdPrize, Config.thirdPrize, Config.thirdPrizeMax, Messages.s45);
            }
            double prizePoolTemp = prizePool;
            if (secondPrize.isEmpty()) {
                //secondPrizeResult = "§b二等奖: §7无";
                secondPrizeResult = Messages.s39;
            } else {
                //secondPrizeResult = "§b二等奖: " + getNamesAndBalance(secondPrize, prizePoolTemp * 0.25, Config.secondPrize, "§a你抽中§6%d§a注§b二等奖§a, 获得了: §b$");
                secondPrizeResult = Messages.s40 + getNamesAndBalance(secondPrize, prizePoolTemp * 0.25, Config.secondPrize, Messages.s46);
                subPrizePool(prizePoolTemp * 0.25);
            }
            if (firstPrize.isEmpty()) {
                //firstPrizeResult = "§c一等奖: §7无";
                firstPrizeResult = Messages.s41;
            } else {
                //firstPrizeResult = "§c一等奖: " + getNamesAndBalance(firstPrize, prizePoolTemp * 0.75, Config.firstPrize, "§c你抽中§6%d§c注§c§l一等奖§c, 获得了: §b$");
                firstPrizeResult = Messages.s42 + getNamesAndBalance(firstPrize, prizePoolTemp * 0.75, Config.firstPrize, Messages.s47);
                subPrizePool(prizePoolTemp * 0.75);
            }
            //broadcastMessage("§6§l开奖结果:");
            broadcastMessage(Messages.s48);
            broadcastMessage(firstPrizeResult);
            broadcastMessage(secondPrizeResult);
            broadcastMessage(thirdPrizeResult);
            broadcastMessage(fifthPrizeResult);
            broadcastMessage(fourthPrizeResult);
            runLotteryFinish = true;
            save();
        });
    }

    private String getNamesAndBalance(HashMap<String, Integer> list, double pool, double prize, String msg) {
        StringBuilder names = new StringBuilder();
        int total = 0;
        Set<Map.Entry<String, Integer>> entrySet = list.entrySet();
        for (Map.Entry<String, Integer> entry : entrySet) {
            names.append("§2").append(entry.getKey()).append(" §7x§6").append(entry.getValue()).append(' ');
            total += entry.getValue();
        }
        double moneyPerBets = (pool + prize) / total;
        for (Map.Entry<String, Integer> entry : entrySet) {
            Player p = plugin.getServer().getPlayerExact(entry.getKey());
            OfflinePlayer player = p == null ? getOfflinePlayer(entry.getKey()) : p;
            double money = moneyPerBets * entry.getValue();
            economy.depositPlayer(player, money);
            if (p != null) {
                sendMessage(p, String.format(msg, entry.getValue()) + formatDecimal(money));
            }
        }
        playSoundAll("ENTITY_PLAYER_LEVELUP");
        return names.toString();
    }

    private String getNamesAndGiveMoney(HashMap<String, Integer> list, double moneyPerBets, double maxMoney, String prize) {
        StringBuilder names = new StringBuilder();
        for (Map.Entry<String, Integer> entry : list.entrySet()) {
            names.append("§2").append(entry.getKey()).append(" §7x§6").append(entry.getValue()).append(' ');
            Player p = plugin.getServer().getPlayerExact(entry.getKey());
            OfflinePlayer player = p == null ? getOfflinePlayer(entry.getKey()) : p;
            double money = Math.min(moneyPerBets * entry.getValue(), maxMoney);
            if (Config.ignorePrizePool || prizePool >= money) { //忽略奖池余额或奖池余额大于等于奖金
                subPrizePool(money); //从奖池里扣钱
                economy.depositPlayer(player, money);
                if (p != null) {
                    playSound(p, "ENTITY_PLAYER_LEVELUP");
                    //sendMessage(p, "§a你抽中§6" + entry.getValue() + "§a注" + prize + "§a, 获得了: §b$" + formatDecimal(money));
                    sendMessage(p, Messages.getMessage(Messages.s49, String.valueOf(entry.getValue()), prize) + formatDecimal(money));
                }
            } else if (prizePool != 0) { //奖池余额小于奖金且奖池余额不为0
                money = prizePool;
                subPrizePool(money);
                economy.depositPlayer(player, money);
                if (p != null) {
                    playSound(p, "ENTITY_PLAYER_LEVELUP");
                    //sendMessage(p, "§a你抽中§6" + entry.getValue() + "§a注" + prize + "§a, 但由于奖池资金不足, 只获得了: §b$" + formatDecimal(money));
                    sendMessage(p, Messages.getMessage(Messages.s50, String.valueOf(entry.getValue()), prize) + formatDecimal(money));
                }
            } else {
                if (p != null) {
                    //sendMessage(p, "§c很遗憾! 虽然你抽中§6" + entry.getValue() + "§c注" + prize + "§c, 但由于奖池资金已经为0, 未能获得奖金。");
                    sendMessage(p, Messages.getMessage(Messages.s51, String.valueOf(entry.getValue()), prize));
                }
            }
        }
        return names.toString();
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer getOfflinePlayer(String player) {
        return plugin.getServer().getOfflinePlayer(player);
    }

    private ArrayList<String> getBets(String player) {
        ConfigurationSection cs = lotteryInfo.getConfigurationSection("bets." + player);
        ArrayList<String> bets = new ArrayList<>();
        if (cs == null) {
            return bets;
        }
        bets.addAll(cs.getKeys(false));
        return bets;
    }

    private ArrayList<String> getPlayers() {
        ConfigurationSection cs = lotteryInfo.getConfigurationSection("bets");
        ArrayList<String> players = new ArrayList<>();
        if (cs == null) {
            return players;
        }
        players.addAll(cs.getKeys(false));
        return players;
    }

    private static String repeat00(int n) {
        if (n == 1) {
            return "00";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i != 0) {
                builder.append(' ');
            }
            builder.append("00");
        }
        return builder.toString();
    }

    private void sendTitleAll(String title, String subtitle, int stay) {
        if (plugin.mcVersion > 10) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.sendTitle(title, subtitle, 10, stay * 20, 10);
            }
        }
    }

    public boolean tryUpdate() {
        if (isRunLottery && !runLotteryFinish) {
            return false;
        }
        file = new File(file.getParent(), getFileName());
        init();
        return true;
    }

    public void forceFalse() {
        if (isRunLottery) {
            isRunLottery = false;
            setValue("isRunLottery", false);
            save();
        }
    }

}
