#!/bin/bash

# Configurar ambiente
export OLLAMA_BASE_URL="http://localhost:11434"

# Verificar se Ollama está rodando
if ! curl -s $OLLAMA_BASE_URL > /dev/null; then
    echo "Error: Ollama is not running on $OLLAMA_BASE_URL"
    echo "Please start Ollama first: ollama serve"
    exit 1
fi

# Verificar se o modelo mistral está disponível
if ! ollama list | grep -q "mistral:latest"; then
    echo "Downloading mistral:latest model..."
    ollama pull mistral:latest
fi

# Compilar e executar
echo "Building project..."
mvn clean compile

echo "Running experiments..."
mvn exec:java -Dexec.mainClass="br.university.project.runner.MainRunner"

echo "Analyzing results..."
mvn exec:java -Dexec.mainClass="br.university.project.analysis.MetricsAnalyzer"

echo "Experiments completed!"