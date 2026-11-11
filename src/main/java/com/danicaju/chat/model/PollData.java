package com.danicaju.chat.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Encuesta interactiva en tiempo real generada mediante el comando /poll.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PollData {

    private String pollId;
    private String question;
    private List<String> options = new ArrayList<>();
    private Map<String, Set<String>> votesByOption = new LinkedHashMap<>();
    private boolean multipleChoice;
    private boolean closed;

    public PollData() {
        this.pollId = UUID.randomUUID().toString();
    }

    public PollData(String question, List<String> options) {
        this();
        this.question = question;
        if (options != null) {
            for (int i = 0; i < options.size(); i++) {
                this.options.add(options.get(i));
                this.votesByOption.put(String.valueOf(i), new LinkedHashSet<>());
            }
        }
    }

    /**
     * Registra o alterna el voto de un usuario para la opción indicada.
     */
    public synchronized void vote(int optionIndex, String username) {
        if (closed || username == null || optionIndex < 0 || optionIndex >= options.size()) {
            return;
        }
        String cleanUser = username.trim();
        String key = String.valueOf(optionIndex);
        votesByOption.computeIfAbsent(key, k -> new LinkedHashSet<>());

        boolean alreadyVotedThis = votesByOption.get(key).stream()
                .anyMatch(u -> u.equalsIgnoreCase(cleanUser));

        if (!multipleChoice) {
            for (Set<String> voters : votesByOption.values()) {
                voters.removeIf(u -> u.equalsIgnoreCase(cleanUser));
            }
        }
        if (!alreadyVotedThis) {
            votesByOption.get(key).add(cleanUser);
        } else if (multipleChoice) {
            votesByOption.get(key).removeIf(u -> u.equalsIgnoreCase(cleanUser));
        }
    }

    @JsonIgnore
    public int getVoteCount(int optionIndex) {
        Set<String> voters = votesByOption.get(String.valueOf(optionIndex));
        return voters != null ? voters.size() : 0;
    }

    @JsonIgnore
    public int getTotalVotes() {
        int total = 0;
        for (int i = 0; i < options.size(); i++) {
            total += getVoteCount(i);
        }
        return total;
    }

    @JsonIgnore
    public boolean hasVoted(int optionIndex, String username) {
        if (username == null) return false;
        Set<String> voters = votesByOption.get(String.valueOf(optionIndex));
        return voters != null && voters.stream().anyMatch(u -> u.equalsIgnoreCase(username.trim()));
    }

    public String getPollId() {
        return pollId;
    }

    public void setPollId(String pollId) {
        this.pollId = pollId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
    }

    public Map<String, Set<String>> getVotesByOption() {
        return votesByOption;
    }

    public void setVotesByOption(Map<String, Set<String>> votesByOption) {
        this.votesByOption = votesByOption != null ? new LinkedHashMap<>(votesByOption) : new LinkedHashMap<>();
    }

    public boolean isMultipleChoice() {
        return multipleChoice;
    }

    public void setMultipleChoice(boolean multipleChoice) {
        this.multipleChoice = multipleChoice;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PollData pollData)) return false;
        return multipleChoice == pollData.multipleChoice &&
                closed == pollData.closed &&
                Objects.equals(pollId, pollData.pollId) &&
                Objects.equals(question, pollData.question) &&
                Objects.equals(options, pollData.options) &&
                Objects.equals(votesByOption, pollData.votesByOption);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pollId, question, options, votesByOption, multipleChoice, closed);
    }
}
