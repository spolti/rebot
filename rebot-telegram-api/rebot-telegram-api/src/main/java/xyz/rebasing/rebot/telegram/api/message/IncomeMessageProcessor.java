/*
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2017 Rebasing.xyz ReBot
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package xyz.rebasing.rebot.telegram.api.message;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.function.Predicate;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.jboss.logging.Logger;
import xyz.rebasing.rebot.api.conf.BotConfig;
import xyz.rebasing.rebot.api.domain.Message;
import xyz.rebasing.rebot.api.domain.MessageUpdate;
import xyz.rebasing.rebot.api.i18n.I18nHelper;
import xyz.rebasing.rebot.api.shared.components.management.message.MessageManagement;
import xyz.rebasing.rebot.api.shared.components.message.sender.OutcomeMessageProcessor;
import xyz.rebasing.rebot.api.spi.CommandProvider;
import xyz.rebasing.rebot.api.spi.PluginProvider;
import xyz.rebasing.rebot.api.spi.administrative.AdministrativeCommandProvider;
import xyz.rebasing.rebot.service.persistence.repository.ApiRepository;
import xyz.rebasing.rebot.service.persistence.repository.LocaleRepository;
import xyz.rebasing.rebot.telegram.api.filter.ReBotPredicate;

import static xyz.rebasing.rebot.telegram.api.filter.ReBotPredicate.isCommand;
import static xyz.rebasing.rebot.telegram.api.filter.ReBotPredicate.messageIsNotNull;
import static xyz.rebasing.rebot.telegram.api.utils.StringUtils.concat;

@ApplicationScoped
public class IncomeMessageProcessor implements Processor {

    private final Logger log = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());
    private boolean isAdministrativeCommand = false;

    private String locale;

    @Inject
    BotConfig config;

    @Inject
    private Instance<CommandProvider> command;
    @Inject
    private Instance<AdministrativeCommandProvider> administrativeCommand;
    @Inject
    private Instance<PluginProvider> plugin;
    @Inject
    private OutcomeMessageProcessor reply;
    @Inject
    private ApiRepository apiRepository;
    @Inject
    private LocaleRepository localeRepository;
    @Inject
    private MessageManagement messageManagement;

    @Override
    public void process(MessageUpdate messageUpdate) {
        locale = localeRepository.get(messageUpdate.getMessage().getChat().getId(), messageUpdate.getMessage().getChat().getTitle());
        if (log.isDebugEnabled()) {
            log.debugv("current message is being processed with the locale: {0}", locale);
        }

        // before proceed with other commands/plugins execute administrative commands
        administrativeCommand.forEach(c -> {

            if (c.canProcessCommand(messageUpdate, config.botUserId())) {
                if (ReBotPredicate.help().test(messageUpdate)) {
                    reply.processOutgoingMessage(new Message(messageUpdate.getMessage().getMessageId(),
                                                             messageUpdate.getMessage().getChat(),
                                                             c.help(locale)),
                                                 c.deleteMessage(),
                                                 c.deleteMessageTimeout());
                    // delete the command itself
                    if (c.deleteMessage()) {
                        messageManagement.deleteMessage(messageUpdate.getMessage().getChat().getId(),
                                                        messageUpdate.getMessage().getMessageId(),
                                                        c.deleteMessageTimeout());
                    }

                    isAdministrativeCommand = true;
                    return;
                }
                reply.processOutgoingMessage(new Message(messageUpdate.getMessage().getMessageId(),
                                                         messageUpdate.getMessage().getChat(),
                                                         c.execute(Optional.of(
                                                                           concat(messageUpdate.getMessage().getText().split(" "))),
                                                                   messageUpdate,
                                                                   locale).toString()),
                                             c.deleteMessage(),
                                             c.deleteMessageTimeout());
                // delete the command itself
                if (c.deleteMessage()) {
                    messageManagement.deleteMessage(messageUpdate.getMessage().getChat().getId(),
                                                    messageUpdate.getMessage().getMessageId(),
                                                    c.deleteMessageTimeout());
                }
                isAdministrativeCommand = true;
                return;
            }
        });

        if (apiRepository.isBotEnabled(messageUpdate.getMessage().getChat().getId())) {
            Predicate predicate = messageIsNotNull().and(isCommand());

            if (predicate.test(messageUpdate)) {
                commandProcessor(messageUpdate);
            } else {
                nonCommandProcessor(messageUpdate);
            }
        } else {
            log.debugv("Bot is disabled for chat: {0}", messageUpdate.getMessage().getChat().getTitle());
        }
    }

    @Override
    public void commandProcessor(MessageUpdate messageUpdate) {

        final StringBuilder response = new StringBuilder("");
        log.debugv("Processing command: {0}", messageUpdate.getMessage().getText());

        String[] args = messageUpdate.getMessage().getText().split(" ");
        String command2process = args[0].replace("@" + config.botUserId(), "");

        // /help command
        // will delete messages within 10 seconds
        if ("/help".equals(command2process)) {
            command.forEach(c -> response.append(c.name() + " - " + c.description(locale) + "\n"));
            administrativeCommand.forEach(ac -> response.append(ac.name() + " - " + ac.description(locale) + "\n"));
            response.append(I18nHelper.resource("Administrative", locale, "internal.help.response"));
            reply.processOutgoingMessage(new Message(messageUpdate.getMessage().getMessageId(), messageUpdate.getMessage().getChat(), response.toString()),
                                         config.deleteMessages(), config.deleteMessagesAfter());
            // delete the command itself
            if (config.deleteMessages()) {
                messageManagement.deleteMessage(messageUpdate.getMessage().getChat().getId(),
                                                messageUpdate.getMessage().getMessageId(),
                                                config.deleteMessagesAfter());
            }
        }

        command.forEach(command -> {
            if (!apiRepository.isCommandEnabled(messageUpdate.getMessage().getChat().getId(), command.name().replace("/", ""))) {
                return;
            } else {
                if (command.canProcessCommand(messageUpdate, config.botUserId())) {
                    if ("help".equals(concat(args))) {
                        response.append(command.help(locale));
                    } else {
                        response.append(command.execute(Optional.of(concat(args)), messageUpdate, locale));
                        log.debugv("COMMAND_PROCESSOR - Command processed, result is: {0}", response);
                    }
                    reply.processOutgoingMessage(new Message(messageUpdate.getMessage().getMessageId(),
                                                             messageUpdate.getMessage().getChat(),
                                                             response.toString()),
                                                 command.deleteMessage(), command.deleteMessageTimeout());

                    // delete the command itself
                    if (command.deleteMessage()) {
                        messageManagement.deleteMessage(messageUpdate.getMessage().getChat().getId(),
                                                        messageUpdate.getMessage().getMessageId(),
                                                        command.deleteMessageTimeout());
                    }
                }
            }
        });
        if (response.length() < 1 && !isAdministrativeCommand) {
            log.debugv("Command [{0}] will not to be processed by this bot or is not an administrative command.",
                       messageUpdate.getMessage().getText() + "");
        }
    }

    @Override
    public void nonCommandProcessor(MessageUpdate messageUpdate) {
        log.debugv("NON_COMMAND_PROCESSOR - Processing message: {0}", messageUpdate.getMessage().toString());
        Message message = new Message(messageUpdate.getMessage().getMessageId(),
                                      messageUpdate.getMessage().getChat());

        plugin.forEach(plugin -> {
            if (!apiRepository.isCommandEnabled(messageUpdate.getMessage().getChat().getId(), plugin.name())) {
                return;
            } else {
                message.setText(plugin.process(messageUpdate, locale));
                try {
                    if (null != message.getText()) {
                        if (message.getText().contains("karma")) {
                            message.setMessageId(0);
                        }
                        reply.processOutgoingMessage(message, plugin.deleteMessage(), plugin.deleteMessageTimeout());
                        // delete the command itself
                        if (plugin.deleteMessage()) {
                            messageManagement.deleteMessage(messageUpdate.getMessage().getChat().getId(),
                                                            messageUpdate.getMessage().getMessageId(),
                                                            plugin.deleteMessageTimeout());
                        }
                    }
                } catch (final Exception e) {
                    log.debug("NON_COMMAND_PROCESSOR - Message not processed by the available plugins.");
                }
            }
        });
    }
}