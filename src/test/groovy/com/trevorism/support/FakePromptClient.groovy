package com.trevorism.support

import com.trevorism.service.PromptClient

class FakePromptClient implements PromptClient {

    final List<Map> questions = []
    final Map<String, Map> answers = [:]
    boolean failing

    @Override
    String askQuestion(Map question) {
        if (failing) {
            throw new IllegalStateException("prompt is down")
        }
        questions << question
        "q${questions.size()}".toString()
    }

    @Override
    Map getAnswer(String answerId) {
        answers[answerId]
    }
}
