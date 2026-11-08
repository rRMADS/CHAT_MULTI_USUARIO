package service;

import model.Usuario;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ClientRegistry {
    private final List<ConexaoCliente> clientes = new ArrayList<>();

    public synchronized void adicionar(ConexaoCliente conexao) {
        if (model.Marca.assistente(conexao.getUsuario().nome()))
            throw new IllegalArgumentException(
                    "Este apelido é reservado ao assistente. Escolha outro.");
        if (buscarPorUsuario(conexao.getUsuario()).isPresent())
            throw new IllegalArgumentException("Este apelido já está em uso. Escolha outro.");
        clientes.add(conexao);
    }

    public synchronized boolean remover(ConexaoCliente conexao) {
        return clientes.remove(conexao);
    }

    public synchronized List<ConexaoCliente> snapshot() {
        return List.copyOf(clientes);
    }

    public synchronized Optional<ConexaoCliente> buscarPorUsuario(Usuario usuario) {
        return clientes.stream().filter(c -> c.getUsuario().equals(usuario)).findFirst();
    }

    public synchronized int quantidade() {
        return clientes.size();
    }
}
