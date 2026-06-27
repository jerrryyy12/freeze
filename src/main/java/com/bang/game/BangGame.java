package com.bang.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * BANG! 한 판의 상태 기계.
 * 1단계 범위: 로비 → 역할/캐릭터 배정 → 턴(다이너마이트·감옥·뽑기) → 다수 카드 효과 → 승리 판정.
 */
public class BangGame {

    public enum State { LOBBY, PLAYING, ENDED }

    public State state = State.LOBBY;
    public UUID host;

    private final Map<UUID, BangPlayer> players = new LinkedHashMap<>();
    private final List<BangPlayer> order = new ArrayList<>();
    private Deck deck;
    private final Random random = new Random();
    private int turnIndex = 0;

    public BangGame(UUID host) { this.host = host; }

    // ===== 로비 =====
    public boolean isInLobby() { return state == State.LOBBY; }
    public boolean isPlaying() { return state == State.PLAYING; }
    public boolean contains(UUID id) { return players.containsKey(id); }
    public BangPlayer get(UUID id) { return players.get(id); }
    public int size() { return players.size(); }
    public List<BangPlayer> seating() { return order; }
    public Deck deck() { return deck; }

    public boolean addPlayer(UUID id, String name) {
        if (state != State.LOBBY || players.containsKey(id) || players.size() >= 7) return false;
        players.put(id, new BangPlayer(id, name));
        return true;
    }

    public boolean removePlayer(UUID id) {
        if (state != State.LOBBY) return false;
        return players.remove(id) != null;
    }

    /** 테스트용 패시브 봇 추가 */
    public boolean addBot(String name) {
        if (state != State.LOBBY || players.size() >= 7) return false;
        BangPlayer bot = new BangPlayer(UUID.randomUUID(), name);
        bot.isBot = true;
        players.put(bot.id, bot);
        return true;
    }

    private boolean anyHumanAlive() {
        for (BangPlayer p : order) if (p.alive && !p.isBot) return true;
        return false;
    }

    public BangPlayer findByName(String name) {
        for (BangPlayer p : order.isEmpty() ? players.values() : order) {
            if (p.name.equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    public boolean isCurrent(UUID id) {
        BangPlayer c = current();
        return c != null && c.id.equals(id);
    }

    // ===== 시작 =====
    public boolean start(MinecraftServer server) {
        if (state != State.LOBBY) return false;
        int n = players.size();
        if (n < 4 || n > 7) return false;

        List<Role> roles = rolesFor(n);
        Collections.shuffle(roles, random);

        order.clear();
        order.addAll(players.values());
        Collections.shuffle(order, random);
        for (int i = 0; i < order.size(); i++) order.get(i).role = roles.get(i);
        int sheriffIdx = 0;
        for (int i = 0; i < order.size(); i++) if (order.get(i).role == Role.SHERIFF) sheriffIdx = i;
        Collections.rotate(order, -sheriffIdx);
        for (int i = 0; i < order.size(); i++) order.get(i).seat = i;

        List<CharacterCard> chars = new ArrayList<>(List.of(CharacterCard.values()));
        Collections.shuffle(chars, random);
        for (int i = 0; i < order.size(); i++) {
            BangPlayer p = order.get(i);
            p.character = chars.get(i);
            p.maxHp = p.character.hp + (p.role == Role.SHERIFF ? 1 : 0);
            p.hp = p.maxHp;
        }

        deck = new Deck(random.nextLong());
        for (BangPlayer p : order) {
            for (int i = 0; i < p.maxHp; i++) {
                Card c = deck.draw();
                if (c != null) p.hand.add(c);
            }
        }

        state = State.PLAYING;
        turnIndex = 0;

        broadcast(server, "§6===== BANG! 시작 (" + n + "명) =====");
        BangPlayer sheriff = order.get(0);
        broadcast(server, "§e보안관: §f" + sheriff.name + " §7(" + sheriff.character.kr + ")");
        StringBuilder seatLine = new StringBuilder("§7좌석 순서: ");
        for (int i = 0; i < order.size(); i++) seatLine.append(i == 0 ? "" : " → ").append(order.get(i).name);
        broadcast(server, seatLine.toString());
        for (BangPlayer p : order) {
            msg(server, p.id, "§7당신의 역할: §f" + p.role.kr + " §8— " + p.role.goal);
            msg(server, p.id, "§7캐릭터: §f" + p.character.kr + " §8(체력 " + p.maxHp + ") — " + p.character.ability);
        }
        BangHeads.rebuild(server, this);
        BangInventory.beginGame(server, this);
        beginTurn(server);
        return true;
    }

    private static List<Role> rolesFor(int n) {
        List<Role> r = new ArrayList<>();
        r.add(Role.SHERIFF);
        r.add(Role.RENEGADE);
        switch (n) {
            case 4 -> { r.add(Role.OUTLAW); r.add(Role.OUTLAW); }
            case 5 -> { r.add(Role.OUTLAW); r.add(Role.OUTLAW); r.add(Role.DEPUTY); }
            case 6 -> { r.add(Role.OUTLAW); r.add(Role.OUTLAW); r.add(Role.OUTLAW); r.add(Role.DEPUTY); }
            case 7 -> { r.add(Role.OUTLAW); r.add(Role.OUTLAW); r.add(Role.OUTLAW); r.add(Role.DEPUTY); r.add(Role.DEPUTY); }
            default -> { }
        }
        return r;
    }

    // ===== 턴 =====
    public BangPlayer current() {
        if (order.isEmpty()) return null;
        return order.get(turnIndex % order.size());
    }

    private BangPlayer nextAlive(BangPlayer from) {
        int idx = order.indexOf(from);
        for (int s = 1; s <= order.size(); s++) {
            BangPlayer nx = order.get((idx + s) % order.size());
            if (nx.alive) return nx;
        }
        return null;
    }

    private void beginTurn(MinecraftServer server) {
        if (state != State.PLAYING) return;
        BangPlayer p = current();
        if (p == null) return;
        if (!p.alive) { advanceTurn(server); return; }
        p.bangsThisTurn = 0;

        // 다이너마이트 판정
        if (p.hasEquip(CardType.DYNAMITE)) {
            Card c = deck.draw();
            if (c != null) {
                deck.discard(c);
                broadcast(server, "§7" + p.name + " 다이너마이트 판정: " + c.rankLabel() + c.suit.sym);
                if (c.suit == Card.Suit.SPADES && c.rank >= 2 && c.rank <= 9) {
                    removeEquip(p, CardType.DYNAMITE);
                    broadcast(server, "§4💥 다이너마이트 폭발! " + p.name + " 3 피해");
                    damage(server, p, 3, null);
                    if (!p.alive) { advanceTurn(server); return; }
                } else {
                    BangPlayer nx = nextAlive(p);
                    if (nx != null && nx != p) {
                        Card dyn = removeEquip(p, CardType.DYNAMITE);
                        if (dyn != null) nx.equipment.add(dyn);
                        broadcast(server, "§7다이너마이트가 " + nx.name + " 에게 넘어갑니다.");
                    }
                }
            }
        }
        if (state != State.PLAYING) return;
        if (!p.alive) { advanceTurn(server); return; }

        // 감옥 판정
        if (p.hasEquip(CardType.JAIL)) {
            Card c = deck.draw();
            if (c != null) {
                deck.discard(c);
                Card jail = removeEquip(p, CardType.JAIL);
                if (jail != null) deck.discard(jail);
                broadcast(server, "§7" + p.name + " 감옥 판정: " + c.rankLabel() + c.suit.sym);
                if (c.suit == Card.Suit.HEARTS) {
                    broadcast(server, "§a" + p.name + " 탈옥 성공! 턴 진행");
                } else {
                    broadcast(server, "§c" + p.name + " 턴을 건너뜁니다(감옥).");
                    advanceTurn(server);
                    return;
                }
            }
        }

        // 뽑기 2장
        for (int i = 0; i < 2; i++) {
            Card c = deck.draw();
            if (c != null) p.hand.add(c);
        }
        broadcast(server, "§b▶ " + p.name + " 님의 턴 §7(체력 " + p.hp + "/" + p.maxHp + ", 손패 " + p.hand.size() + ")");
        BangTable.render(server, this);
        BangInventory.syncAll(server, this);
        if (p.isBot) {
            // 패시브 봇: 사람이 살아있으면 바로 턴 종료(아니면 무한 재귀 방지로 멈춤)
            if (anyHumanAlive()) endTurn(server, p);
            return;
        }
        msg(server, p.id, "§a당신의 턴! §7/bang hand 확인 · /bang play <번호> [대상] · /bang end");
    }

    public void endTurn(MinecraftServer server, BangPlayer p) {
        if (!isCurrent(p.id)) return;
        while (p.hand.size() > p.handLimit()) {
            deck.discard(p.hand.remove(p.hand.size() - 1));
        }
        advanceTurn(server);
    }

    private void advanceTurn(MinecraftServer server) {
        if (state != State.PLAYING) return;
        for (int step = 1; step <= order.size(); step++) {
            int idx = (turnIndex + step) % order.size();
            if (order.get(idx).alive) {
                turnIndex = idx;
                beginTurn(server);
                return;
            }
        }
    }

    // ===== 거리/사정 =====
    public int distance(BangPlayer from, BangPlayer to) {
        List<BangPlayer> aliveList = new ArrayList<>();
        for (BangPlayer p : order) if (p.alive) aliveList.add(p);
        int a = aliveList.indexOf(from), b = aliveList.indexOf(to);
        if (a < 0 || b < 0) return Integer.MAX_VALUE;
        int n = aliveList.size();
        int d = Math.min(Math.abs(a - b), n - Math.abs(a - b));
        if (to.hasEquip(CardType.MUSTANG)) d += 1;
        if (to.character == CharacterCard.PAUL_REGRET) d += 1;
        if (from.hasEquip(CardType.SCOPE)) d -= 1;
        if (from.character == CharacterCard.ROSE_DOOLAN) d -= 1;
        return Math.max(1, d);
    }

    public boolean inRange(BangPlayer from, BangPlayer to) {
        return distance(from, to) <= from.weaponRange();
    }

    // ===== 카드 플레이 =====
    /** index0: 0-기준 손패 인덱스. targetName: 대상 이름(없으면 null). */
    public void playCard(MinecraftServer server, BangPlayer actor, int index0, String targetName) {
        if (!isPlaying()) { msg(server, actor.id, "§c진행 중인 게임이 없습니다."); return; }
        if (!isCurrent(actor.id)) { msg(server, actor.id, "§c당신의 턴이 아닙니다."); return; }
        if (index0 < 0 || index0 >= actor.hand.size()) { msg(server, actor.id, "§c손패 번호가 잘못됐습니다."); return; }
        Card card = actor.hand.get(index0);
        CardType t = card.type;
        BangPlayer target = targetName == null ? null : findByName(targetName);

        switch (t) {
            case BANG -> doBang(server, actor, index0, target);
            case MISSED -> msg(server, actor.id, "§7빗나감은 방어 전용이라 직접 낼 수 없습니다.");
            case BEER -> {
                if (aliveCount() <= 2) { msg(server, actor.id, "§c2명만 남으면 맥주는 효과가 없습니다."); return; }
                if (actor.hp >= actor.maxHp) { msg(server, actor.id, "§c체력이 가득 찼습니다."); return; }
                consume(actor, index0);
                actor.hp++;
                broadcast(server, "§a" + actor.name + " 맥주로 체력 회복 (" + actor.hp + "/" + actor.maxHp + ")");
            }
            case SALOON -> {
                consume(actor, index0);
                for (BangPlayer p : order) if (p.alive && p.hp < p.maxHp) p.hp++;
                broadcast(server, "§a" + actor.name + " 술집! 모두 체력 1 회복");
            }
            case STAGECOACH -> { consume(actor, index0); drawTo(actor, 2); broadcast(server, "§7" + actor.name + " 역마차: 2장 뽑음"); }
            case WELLS_FARGO -> { consume(actor, index0); drawTo(actor, 3); broadcast(server, "§7" + actor.name + " 웰스 파고: 3장 뽑음"); }
            case GENERAL_STORE -> {
                consume(actor, index0);
                for (BangPlayer p : order) if (p.alive) drawTo(p, 1);
                broadcast(server, "§7" + actor.name + " 잡화점: 모두 1장 뽑음 §8(간이 구현)");
            }
            case GATLING -> {
                consume(actor, index0);
                broadcast(server, "§c" + actor.name + " 개틀링! 다른 모두에게 BANG!");
                for (BangPlayer p : new ArrayList<>(order)) if (p.alive && p != actor) resolveShot(server, p, actor, 1);
            }
            case INDIANS -> {
                consume(actor, index0);
                broadcast(server, "§c" + actor.name + " 인디언 습격!");
                for (BangPlayer p : new ArrayList<>(order)) {
                    if (!p.alive || p == actor) continue;
                    Card b = p.takeCard(CardType.BANG);
                    if (b != null) { deck.discard(b); broadcast(server, "§7" + p.name + " BANG!으로 방어"); }
                    else damage(server, p, 1, actor);
                }
            }
            case DUEL -> {
                if (target == null || !target.alive) { msg(server, actor.id, "§c대상을 지정하세요: /bang play <번호> <이름>"); return; }
                consume(actor, index0);
                broadcast(server, "§c" + actor.name + " → " + target.name + " 결투!");
                doDuel(server, actor, target);
            }
            case PANIC -> {
                if (target == null || !target.alive || target == actor) { msg(server, actor.id, "§c대상을 지정하세요."); return; }
                if (distance(actor, target) > 1) { msg(server, actor.id, "§c공황은 거리 1만 가능합니다."); return; }
                consume(actor, index0);
                if (steal(actor, target)) broadcast(server, "§7" + actor.name + " 공황: " + target.name + " 카드 1장 빼앗음");
                else broadcast(server, "§7" + target.name + " 가져올 카드가 없습니다.");
            }
            case CAT_BALOU -> {
                if (target == null || !target.alive || target == actor) { msg(server, actor.id, "§c대상을 지정하세요."); return; }
                consume(actor, index0);
                if (discardRandom(target)) broadcast(server, "§7" + actor.name + " 캣 발루: " + target.name + " 카드 1장 버림");
                else broadcast(server, "§7" + target.name + " 버릴 카드가 없습니다.");
            }
            case JAIL -> {
                if (target == null || !target.alive || target.role == Role.SHERIFF) { msg(server, actor.id, "§c보안관 외 대상을 지정하세요."); return; }
                if (target.hasEquip(CardType.JAIL)) { msg(server, actor.id, "§c이미 감옥에 있습니다."); return; }
                target.equipment.add(consume(actor, index0));
                broadcast(server, "§7" + actor.name + " → " + target.name + " 감옥에 가둠");
            }
            default -> { // WEAPON / EQUIP(통·조준경·머스탱) / DYNAMITE
                if (t == CardType.DYNAMITE) {
                    if (actor.hasEquip(CardType.DYNAMITE)) { msg(server, actor.id, "§c이미 다이너마이트가 있습니다."); return; }
                    actor.equipment.add(consume(actor, index0));
                    broadcast(server, "§7" + actor.name + " 다이너마이트 설치");
                } else if (t.category == CardType.Category.WEAPON) {
                    // 기존 무기 교체
                    for (int i = actor.equipment.size() - 1; i >= 0; i--) {
                        if (actor.equipment.get(i).type.category == CardType.Category.WEAPON) deck.discard(actor.equipment.remove(i));
                    }
                    actor.equipment.add(consume(actor, index0));
                    broadcast(server, "§7" + actor.name + " 무기 장착: " + t.kr + " (사정 " + t.weaponRange + ")");
                } else if (t.category == CardType.Category.EQUIP) {
                    if (actor.hasEquip(t)) { msg(server, actor.id, "§c같은 장비를 이미 가지고 있습니다."); return; }
                    actor.equipment.add(consume(actor, index0));
                    broadcast(server, "§7" + actor.name + " 장비: " + t.kr);
                } else {
                    msg(server, actor.id, "§7" + t.kr + " 은(는) 다음 단계에서 구현됩니다.");
                }
            }
        }
        checkWin(server);
        BangTable.render(server, this);
        BangInventory.syncAll(server, this);
    }

    private void doBang(MinecraftServer server, BangPlayer actor, int index0, BangPlayer target) {
        boolean unlimited = actor.hasEquip(CardType.VOLCANIC) || actor.character == CharacterCard.WILLY_THE_KID;
        if (!unlimited && actor.bangsThisTurn >= 1) { msg(server, actor.id, "§c이번 턴 BANG! 사용 제한입니다."); return; }
        if (target == null || !target.alive || target == actor) { msg(server, actor.id, "§c유효한 대상을 지정하세요: /bang play <번호> <이름>"); return; }
        if (!inRange(actor, target)) { msg(server, actor.id, "§c사정거리 밖 (거리 " + distance(actor, target) + ", 사정 " + actor.weaponRange() + ")"); return; }
        consume(actor, index0);
        actor.bangsThisTurn++;
        int need = actor.character == CharacterCard.SLAB_THE_KILLER ? 2 : 1;
        broadcast(server, "§c" + actor.name + " §7→ §c" + target.name + " §7BANG!" + (need > 1 ? " (빗나감 2장 필요)" : ""));
        resolveShot(server, target, actor, need);
    }

    private void doDuel(MinecraftServer server, BangPlayer attacker, BangPlayer target) {
        BangPlayer turn = target;        // 대상이 먼저 반응
        BangPlayer other = attacker;
        while (true) {
            Card b = turn.takeCard(CardType.BANG);
            if (b == null && turn.character == CharacterCard.CALAMITY_JANET) b = turn.takeCard(CardType.MISSED);
            if (b == null) { damage(server, turn, 1, other); return; }
            deck.discard(b);
            BangPlayer tmp = turn; turn = other; other = tmp;
        }
    }

    public void resolveShot(MinecraftServer server, BangPlayer target, BangPlayer source, int missesNeeded) {
        int saved = 0;
        if (target.hasEquip(CardType.BARREL)) {
            Card check = deck.draw();
            if (check != null) {
                deck.discard(check);
                broadcast(server, "§7" + target.name + " 통 판정: " + check.rankLabel() + check.suit.sym);
                if (check.suit == Card.Suit.HEARTS) saved++;
            }
        }
        while (saved < missesNeeded) {
            Card miss = target.takeCard(CardType.MISSED);
            if (miss == null && target.character == CharacterCard.CALAMITY_JANET) miss = target.takeCard(CardType.BANG);
            if (miss == null) break;
            deck.discard(miss);
            saved++;
        }
        if (saved >= missesNeeded) broadcast(server, "§a" + target.name + " 빗나감으로 막음");
        else damage(server, target, 1, source);
    }

    public void damage(MinecraftServer server, BangPlayer target, int amount, BangPlayer source) {
        for (int i = 0; i < amount; i++) {
            if (!target.alive) return;
            target.hp--;
            if (target.character == CharacterCard.BART_CASSIDY) drawTo(target, 1);
            broadcast(server, "§7" + target.name + " 체력 " + target.hp + "/" + target.maxHp);
            if (target.hp <= 0) tryBeerOrDie(server, target, source);
        }
    }

    private void tryBeerOrDie(MinecraftServer server, BangPlayer target, BangPlayer source) {
        while (target.hp <= 0 && aliveCount() > 2 && target.hasCard(CardType.BEER)) {
            deck.discard(target.takeCard(CardType.BEER));
            target.hp = 1;
            broadcast(server, "§a" + target.name + " 맥주로 버팀! (체력 1)");
        }
        if (target.hp <= 0) die(server, target, source);
    }

    private void die(MinecraftServer server, BangPlayer target, BangPlayer killer) {
        target.alive = false;
        if (!target.isBot) {
            ServerPlayer sp = server.getPlayerList().getPlayer(target.id);
            if (sp != null) sp.setGameMode(GameType.SPECTATOR);
        }
        broadcast(server, "§4☠ " + target.name + " 탈락! §7역할: " + target.role.kr);
        for (Card c : target.hand) deck.discard(c);
        for (Card c : target.equipment) deck.discard(c);
        target.hand.clear();
        target.equipment.clear();
        if (killer != null && killer.alive) {
            if (target.role == Role.OUTLAW) {
                drawTo(killer, 3);
                msg(server, killer.id, "§a무법자 제거 보상: 카드 3장");
            } else if (target.role == Role.DEPUTY && killer.role == Role.SHERIFF) {
                for (Card c : killer.hand) deck.discard(c);
                killer.hand.clear();
                msg(server, killer.id, "§c부관을 죽여 손패를 모두 잃음");
            }
        }
        checkWin(server);
    }

    private int aliveCount() {
        int c = 0;
        for (BangPlayer p : order) if (p.alive) c++;
        return c;
    }

    private void checkWin(MinecraftServer server) {
        if (state != State.PLAYING) return;
        BangPlayer sheriff = null;
        int outlaws = 0, renegades = 0, alive = 0;
        for (BangPlayer p : order) {
            if (!p.alive) continue;
            alive++;
            if (p.role == Role.SHERIFF) sheriff = p;
            if (p.role == Role.OUTLAW) outlaws++;
            if (p.role == Role.RENEGADE) renegades++;
        }
        if (sheriff == null) {
            if (alive == 1 && renegades == 1) finish(server, "§5배신자 승리!");
            else finish(server, "§c무법자 승리!");
        } else if (outlaws == 0 && renegades == 0) {
            finish(server, "§e보안관 진영 승리!");
        }
    }

    private void finish(MinecraftServer server, String result) {
        state = State.ENDED;
        BangHeads.clear();
        BangInventory.endGame(server, this);
        broadcast(server, "§6===== 게임 종료 — " + result + " §6=====");
        for (BangPlayer p : order) broadcast(server, "§7" + p.name + ": " + p.role.kr + (p.alive ? "" : " §8(탈락)"));
    }

    // ===== 보조 =====
    private Card consume(BangPlayer p, int index0) {
        Card c = p.hand.remove(index0);
        if (!c.type.staysInPlay()) deck.discard(c);
        return c;
    }

    private Card removeEquip(BangPlayer p, CardType type) {
        for (int i = 0; i < p.equipment.size(); i++) if (p.equipment.get(i).type == type) return p.equipment.remove(i);
        return null;
    }

    private void drawTo(BangPlayer p, int count) {
        for (int i = 0; i < count; i++) {
            Card c = deck.draw();
            if (c != null) p.hand.add(c);
        }
    }

    private boolean steal(BangPlayer thief, BangPlayer victim) {
        if (!victim.hand.isEmpty()) { thief.hand.add(victim.hand.remove(random.nextInt(victim.hand.size()))); return true; }
        if (!victim.equipment.isEmpty()) { thief.hand.add(victim.equipment.remove(random.nextInt(victim.equipment.size()))); return true; }
        return false;
    }

    private boolean discardRandom(BangPlayer victim) {
        if (!victim.hand.isEmpty()) { deck.discard(victim.hand.remove(random.nextInt(victim.hand.size()))); return true; }
        if (!victim.equipment.isEmpty()) { deck.discard(victim.equipment.remove(random.nextInt(victim.equipment.size()))); return true; }
        return false;
    }

    // ===== 메시지 =====
    public void msg(MinecraftServer server, UUID id, String text) {
        ServerPlayer sp = server.getPlayerList().getPlayer(id);
        if (sp != null) sp.sendSystemMessage(Component.literal(text));
    }

    public void broadcast(MinecraftServer server, String text) {
        for (BangPlayer p : order.isEmpty() ? players.values() : order) msg(server, p.id, text);
    }
}
