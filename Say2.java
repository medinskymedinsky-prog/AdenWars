package net.sf.l2j.gameserver.network.clientpackets;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.handler.ChatHandler;
import net.sf.l2j.gameserver.handler.IChatHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.model.actor.ai.AutoFarmManager;

// Импорты для автофарма
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.data.cache.HtmCache;

public final class Say2 extends L2GameClientPacket
{
	private static final Logger CHAT_LOG = Logger.getLogger("chat");
	
	private static final String[] WALKER_COMMAND_LIST =
	{
		"USESKILL",
		"USEITEM",
		"BUYITEM",
		"SELLITEM",
		"SAVEITEM",
		"LOADITEM",
		"MSG",
		"DELAY",
		"LABEL",
		"JMP",
		"CALL",
		"RETURN",
		"MOVETO",
		"NPCSEL",
		"NPCDLG",
		"DLGSEL",
		"CHARSTATUS",
		"POSOUTRANGE",
		"POSINRANGE",
		"GOHOME",
		"SAY",
		"EXIT",
		"PAUSE",
		"STRINDLG",
		"STRNOTINDLG",
		"CHANGEWAITTYPE",
		"FORCEATTACK",
		"ISMEMBER",
		"REQUESTJOINPARTY",
		"REQUESTOUTPARTY",
		"QUITPARTY",
		"MEMBERSTATUS",
		"CHARBUFFS",
		"ITEMCOUNT",
		"FOLLOWTELEPORT"
	};
	
	private String _text;
	private int _id;
	private String _target;
	
	@Override
	protected void readImpl()
	{
		_text = readS();
		_id = readD();
		_target = (_id == SayType.TELL.ordinal()) ? readS() : null;
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getClient().getPlayer();
		if (player == null)
			return;
		
		// ========== ПЕРВЫЙ ПРИОРИТЕТ: ОБРАБОТКА КОМАНД АВТОФАРМА ==========
		if (_text.startsWith(".") || _text.startsWith("//"))
		{
			String command = _text.startsWith(".") ? _text.substring(1) : _text.substring(2);
			command = command.toLowerCase().trim();
			
			if (command.startsWith("autofarm"))
			{
				processAutoFarmCommand(player, command);
				return; // ВАЖНО: выходим чтобы не обрабатывать как обычный чат
			}
		}
		// ========== КОНЕЦ АВТОФАРМА ==========
		
		// Оригинальная логика обработки чата
		if (_id < 0 || _id >= SayType.VALUES.length)
			return;
		
		if (_text.isEmpty() || _text.length() > 100)
			return;
		
		SayType type = SayType.VALUES[_id];
		if (Config.L2WALKER_PROTECTION && type == SayType.TELL && checkBot(_text))
			return;
		
		if (!player.isGM() && (type == SayType.ANNOUNCEMENT || type == SayType.CRITICAL_ANNOUNCE))
			return;
		
		if (player.isChatBanned() || (player.isInJail() && !player.isGM()))
		{
			player.sendPacket(SystemMessageId.CHATTING_PROHIBITED);
			return;
		}
		
		if (type == SayType.PETITION_PLAYER && player.isGM())
			type = SayType.PETITION_GM;
		
		if (Config.LOG_CHAT)
		{
			final LogRecord logRecord = new LogRecord(Level.INFO, _text);
			logRecord.setLoggerName("chat");
			
			if (type == SayType.TELL)
				logRecord.setParameters(new Object[]
				{
					type,
					"[" + player.getName() + " to " + _target + "]"
				});
			else
				logRecord.setParameters(new Object[]
				{
					type,
					"[" + player.getName() + "]"
				});
			
			CHAT_LOG.log(logRecord);
		}
		
		_text = _text.replaceAll("\\\\n", "");
		
		final IChatHandler handler = ChatHandler.getInstance().getHandler(type);
		if (handler == null)
		{
			LOGGER.warn("{} tried to use unregistred chathandler type: {}.", player.getName(), type);
			return;
		}
		
		handler.handleChat(type, player, _target, _text);
	}
	
	// ========== МЕТОДЫ АВТОФАРМА ==========
	
	/**
	 * Обработка конкретных команд автофарма
	 */
	private void processAutoFarmCommand(Player player, String command)
	{
		String[] params = command.split(" ");
		
		try
		{
			if (params.length == 1)
			{
				// Команда: .autofarm - показать главное меню
				showAutoFarmMainMenu(player);
				return;
			}
			
			switch (params[1])
			{
				case "start":
					int radius = 500;
					if (params.length > 2)
					{
						try
						{
							radius = Integer.parseInt(params[2]);
							if (radius < 100) radius = 100;
							if (radius > 2000) radius = 2000;
						}
						catch (NumberFormatException e)
						{
							player.sendMessage("Некорректный радиус. Используйте: .autofarm start [радиус]");
							return;
						}
					}
					startAutoFarm(player, radius);
					break;
					
				case "stop":
					stopAutoFarm(player);
					break;
					
				case "status":
					showAutoFarmStatus(player);
					break;
					
				case "stats":
					showAutoFarmStats(player);
					break;
					
				case "settings":
					showAutoFarmSettings(player);
					break;
					
				case "help":
					showAutoFarmHelp(player);
					break;
					
				default:
					player.sendMessage("Неизвестная команда автофарма. Используйте: .autofarm help");
			}
		}
		catch (Exception e)
		{
			player.sendMessage("Ошибка выполнения команды автофарма");
			LOGGER.warn("AutoFarm command error: " + e.getMessage());
		}
	}
	
	/**
	 * Показать главное меню автофарма
	 */
	private void showAutoFarmMainMenu(Player player)
	{
		String html = HtmCache.getInstance().getHtm("data/html/mods/autofarm/main.htm");
		if (html == null)
		{
			// Если файл не найден, покажем базовое меню
			html = getBasicAutoFarmMenu();
		}
		else
		{
			// Заменяем переменные в HTML
			boolean isFarming = isPlayerFarming(player); // Временная заглушка
			html = html.replace("%status%", isFarming ? "<font color=00FF00>АКТИВЕН</font>" : "<font color=FF0000>НЕАКТИВЕН</font>");
			html = html.replace("%mobs_killed%", "0");
			html = html.replace("%items_looted%", "0");
		}
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html);
		player.sendPacket(msg);
	}
	
	/**
	 * Базовое меню автофарма (если HTML файл не найден)
	 */
	private String getBasicAutoFarmMenu()
	{
		return "<html><body>" +
			   "<center><h1>🤖 Система Автофарма</h1></center>" +
			   "<br>" +
			   "<table width=300>" +
			   "<tr><td><button value=\"🚀 Старт фарма\" action=\"bypass -h autofarm_start\" width=200 height=30></td></tr>" +
			   "<tr><td><button value=\"⏹️ Стоп фарма\" action=\"bypass -h autofarm_stop\" width=200 height=30></td></tr>" +
			   "<tr><td><button value=\"⚙️ Настройки\" action=\"bypass -h autofarm_settings\" width=200 height=30></td></tr>" +
			   "<tr><td><button value=\"📊 Статистика\" action=\"bypass -h autofarm_stats\" width=200 height=30></td></tr>" +
			   "</table>" +
			   "<br><center><font color=LEVEL>Используйте команды:</font><br>" +
			   ".autofarm start - запустить<br>" +
			   ".autofarm stop - остановить<br>" +
			   ".autofarm status - статус</center>" +
			   "</body></html>";
	}
	
/**
 * Запуск автофарма
 */
private void startAutoFarm(Player player, int radius)
{
    AutoFarmManager.getInstance().startAutoFarm(player, radius);
}

/**
 * Остановка автофарма
 */
private void stopAutoFarm(Player player)
{
    AutoFarmManager.getInstance().stopAutoFarm(player);
}
	
	/**
	 * Показать статус автофарма
	 */
	private void showAutoFarmStatus(Player player)
	{
		boolean isFarming = isPlayerFarming(player);
		player.sendMessage("📊 Статус автофарма: " + (isFarming ? "🟢 АКТИВЕН" : "🔴 НЕАКТИВЕН"));
		if (isFarming)
		{
			player.sendMessage("📏 Радиус: " + player.getFarmRadius() + "px");
		}
	}
	
	/**
	 * Показать статистику
	 */
	private void showAutoFarmStats(Player player)
	{
		player.sendMessage("📈 Статистика фарма:");
		player.sendMessage("🎯 Убито мобов: 0");
		player.sendMessage("📦 Собрано лута: 0");
		player.sendMessage("⏱️ Время фарма: 0 мин");
	}
	
	/**
	 * Показать настройки
	 */
	private void showAutoFarmSettings(Player player)
	{
		player.sendMessage("⚙️ Настройки автофарма:");
		player.sendMessage("📏 Радиус поиска: " + player.getFarmRadius() + "px");
		player.sendMessage("❤️ Авто-хил: Вкл");
		player.sendMessage("📦 Авто-лут: Вкл");
		player.sendMessage("✨ Авто-бафф: Вкл");
	}
	
	/**
	 * Показать справку
	 */
	private void showAutoFarmHelp(Player player)
	{
		player.sendMessage("📖 Команды автофарма:");
		player.sendMessage(".autofarm - главное меню");
		player.sendMessage(".autofarm start [радиус] - запуск (радиус 100-2000)");
		player.sendMessage(".autofarm stop - остановка");
		player.sendMessage(".autofarm status - статус");
		player.sendMessage(".autofarm stats - статистика");
		player.sendMessage(".autofarm settings - настройки");
	}
	
	/**
	 * Временный метод для проверки статуса фарма
	 */
	private boolean isPlayerFarming(Player player)
	{
		return player.isAutoFarm(); // Временная заглушка
	}
	
	/**
	 * Запуск задачи автофарма
	 */
	private void startFarmTask(Player player)
	{
		// TODO: Реализовать полноценную задачу автофарма
		// Это временная заглушка
		player.sendMessage("🔧 Система автофарма в разработке...");
	}
	// ========== КОНЕЦ МЕТОДОВ АВТОФАРМА ==========
	
	private static boolean checkBot(String text)
	{
		for (String botCommand : WALKER_COMMAND_LIST)
		{
			if (text.startsWith(botCommand))
				return true;
		}
		return false;
	}
	
	@Override
	protected boolean triggersOnActionRequest()
	{
		return false;
	}
}