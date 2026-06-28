package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonAttributeServiceTest {

    @Mock
    private ImdbCatalog imdbCatalogMock;

    @Mock
    private Model modelMock;

    @Test
    void add() {
        final var unitUnderTest = new CommonAttributeService(imdbCatalogMock);
        final var nameOfList = "some list name";
        when(imdbCatalogMock.getNameOfList()).thenReturn(nameOfList);

        unitUnderTest.add(modelMock);


        verify(imdbCatalogMock).getNameOfList();
        verify(modelMock).addAttribute("selectedList", nameOfList);
    }
}