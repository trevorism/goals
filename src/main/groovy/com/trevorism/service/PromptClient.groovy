package com.trevorism.service

interface PromptClient {

    String askQuestion(Map question)

    Map getAnswer(String answerId)
}
